# due

Consultas à API da Declaração Única de Exportação (DUE) do Portal Único Siscomex, sobre a `siscomex`. Veja o [README](../../../../../../README.md) principal para autenticação, sessão e erros de comunicação.

A `DueClient` é somente leitura: cobre os serviços de consulta da API da DUE. Criação e alteração de declaração (`POST`/`PUT /ext/due`) e vinculação de atos concessórios (`PUT /ext/due/{numero}/ato-concessorio-isencao`) não são implementadas.

## Uso rápido

```java
import com.souhenriza.Siscomex;
import com.souhenriza.due.DueClient;
import com.souhenriza.due.model.*;

DueClient due = new DueClient(siscomex);

DueDados dados = due.consultarDadosPorNumeroDue(NumeroDue.of("21BR0000056196"));
```

Internamente a `DueClient` usa `Siscomex.send`, então vale tudo que o README principal descreve sobre sessão, tokens e a janela de parada programada. Também existe um construtor `DueClient(SiscomexGateway)` para testes, sem depender de uma `Siscomex` real.

## Métodos

| Método | Endpoint | Descrição |
|---|---|---|
| `consultarLinkDues(ChaveDeAcessoNFe)` | `GET /ext/due` | Links das DUEs vinculadas à chave de acesso de uma NF-e. |
| `consultarDadosResumidosDue(NumeroDue, String ruc)` | `GET /ext/due/consultarDadosResumidosDUE` | Dados resumidos, por número da DUE **ou** RUC (um dos dois, nunca os dois). |
| `consultarDadosPorNumeroDue(NumeroDue)` | `GET /ext/due/numero-da-due/{numero}` | Dados completos, pelo número da DUE. |
| `consultarDadosPorNumeroRUC(String ruc)` | `GET /ext/due/numero-da-ruc/{numero}` | Dados completos, pelo RUC (referência única de carga). |
| `consultarAtosConcessoriosIsencao(...)` | `GET /ext/due/{numero}/drawback/isencao/atos-concessorios` | Atos concessórios de drawback — isenção. |
| `consultarAtosConcessionariosSuspensao(...)` | `GET /ext/due/{numero}/drawback/suspensao/atos-concessorios` | Atos concessórios de drawback — suspensão. |

### Links de DUEs por chave de acesso de NF-e

```java
ChaveDeAcessoNFe chave = new ChaveDeAcessoNFe("35200714200166000166550010000000046550000760");
List<LinksDue> links = due.consultarLinkDues(chave);
```

`ChaveDeAcessoNFe` valida o tamanho (44 caracteres) e o dígito verificador ao ser criada — antes de qualquer chamada de rede.

### Dados resumidos

```java
DadosResumidosDue resumo = due.consultarDadosResumidosDue(NumeroDue.of("21BR0000056196"), null);
// ou, pelo RUC:
DadosResumidosDue resumo = due.consultarDadosResumidosDue(null, "9BR00000001000000000000000000023366");
```

Informar os dois (ou nenhum) lança `DueException`.

### Dados completos

```java
DueDados porNumero = due.consultarDadosPorNumeroDue(NumeroDue.of("21BR0000056196"));
DueDados porRuc = due.consultarDadosPorNumeroRUC("9BR00000001000000000000000000023366");
```

### Atos concessórios de drawback

```java
List<AtosConcessoriosIsencao> isencao = due.consultarAtosConcessoriosIsencao(
        NumeroDue.of("21BR0000056196"),
        null,                                       // numeroItemDeDue
        0, 49,                                       // offset, limit
        null, null, null,                            // numeroAtoConcessionario, numeroDoItemDoAtoAcessorio, cnpjBeneficiario
        AtoConcessionarioOrdenacao.NUMERO,
        AtoConcessionarioTipoOrdenacao.ASC);

List<AtosConcessionarosSuspensao> suspensao = due.consultarAtosConcessionariosSuspensao(
        NumeroDue.of("21BR0000056196"), null, 0, 49, null, null, null, null, null);
```

Todos os filtros além do número da DUE são opcionais (`null` omite o parâmetro da URL — ver `Query`).

## Validação dos valores

- `NumeroDue`: formato `NNAANNNNNNNNNN` (14 caracteres). Validado ao criar o objeto.
- `ChaveDeAcessoNFe`: 44 caracteres com dígito verificador conferido na construção.
- RUC (`String`): validado internamente pela `DueClient` no formato `NAA` + 32 dígitos (35 caracteres), para os métodos que o recebem como texto solto.

Valores inválidos lançam `DueException` antes de qualquer chamada ao portal.

## Erros

Erros de negócio e respostas inesperadas do portal (404, 422, 5xx) viram `DueException`, que estende a `PortalException` comum a `DueClient` e `DuimpClient`:

```java
try {
    due.consultarDadosPorNumeroDue(numeroDue);
} catch (DueException e) {
    e.getMessage();
    e.status();          // código HTTP devolvido pelo portal, ou 0 quando o erro é local (validação)
    e.codigoInterno();    // código interno do portal, quando informado
    e.erros();             // List<ErroNegocio>, detalhamento do portal
    e.isNotFound();        // true para 404
    e.isBusinessError();   // true para 422
    e.isRetryable();       // true para 5xx
}
```

Falhas de autenticação e de comunicação (sessão, rede, janela de parada programada) continuam sendo `SiscomexException`, como descrito no README principal.

## Escopo atual

Implementado: todos os serviços de **consulta** (`GET`) da API da DUE.

Ainda não implementado:

- `POST /ext/due` — criação de declaração.
- `PUT /ext/due/{numero}` — alteração de declaração.
- `PUT /ext/due/{numero}/ato-concessorio-isencao` — vinculação de atos concessórios de drawback.
- `GET /ext/due/{numero}/exigencias-fiscais` — lista de exigências fiscais da DUE.
