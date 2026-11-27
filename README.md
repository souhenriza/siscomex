# Aduana4j

Biblioteca Java **não oficial** para o Portal Único de Comércio Exterior (Siscomex / PUCOMEX).

Ela cuida do que é comum a todos os serviços do portal:

- autenticação com certificado digital (e-CPF / e-CNPJ) ou com chave de acesso;
- sessão (tokens JWT e CSRF), renovada sozinha;
- respeito à janela de parada e ao limite de autenticações;
- tradução dos erros de autenticação em exceções com código e tempo de espera.

Em cima disso há dois clientes de serviço já prontos: **DUE** (consultas) e **DUIMP** (consultas e envio dos dados gerais).

> A biblioteca é para **entidades privadas**. Autenticação de sistema integrado, de uso da administração pública, não faz parte dela.

---

## Sumário

1. [Requisitos e instalação](#requisitos-e-instalação)
2. [Início rápido](#início-rápido)
3. [Conceitos](#conceitos)
4. [Ambientes](#ambientes)
5. [Credenciais](#credenciais)
6. [Sessão e tokens](#sessão-e-tokens)
7. [Requisições genéricas](#requisições-genéricas)
8. [Serviço DUIMP](#serviço-duimp)
9. [Serviço DUE](#serviço-due)
10. [Erros](#erros)
11. [Janela de parada programada](#janela-de-parada-programada)
12. [Logs](#logs)
13. [Testes e CI](#testes-e-ci)
14. [Segurança: o que você deve fazer](#segurança-o-que-você-deve-fazer)
15. [Escopo e limitações](#escopo-e-limitações)
16. [Organização do código](#organização-do-código)
17. [Licença](#licença)

---

## Requisitos e instalação

- Java 21 ou superior
- Maven

A biblioteca ainda não está publicada em um repositório Maven. Para usá-la, instale-a no seu repositório local:

```bash
mvn install
```

Depois declare a dependência no seu projeto:

```xml
<dependency>
    <groupId>io.github.souhenriza</groupId>
    <artifactId>aduana4j</artifactId>
    <version>0.0.1</version>
</dependency>
```

A biblioteca usa SLF4J para logs, mas não traz uma implementação. Adicione uma no seu projeto (veja [Logs](#logs)).

---

## Início rápido

Consulta de uma DUIMP com certificado A1:

```java
import com.souhenriza.Ambiente;
import com.souhenriza.Environment;
import com.souhenriza.Siscomex;
import com.souhenriza.autenticacao.PerfilAcesso;
import com.souhenriza.autenticacao.api.RequisicaoSiscomex;
import com.souhenriza.autenticacao.api.RespostaSiscomex;
import com.souhenriza.autenticacao.credencial.CertificadoA1;

import java.nio.file.Path;

public class Exemplo {

    public static void main(String[] args) {
        // A senha vem de variável de ambiente, nunca do código-fonte.
        char[] senha = System.getenv("SISCOMEX_SENHA").toCharArray();

        CertificadoA1 certificado = CertificadoA1.deArquivo(Path.of("certificado.pfx"), senha, PerfilAcesso.IMPEXP);
        Siscomex siscomex = new Siscomex(Ambiente.HOMOLOGACAO, certificado);

        RespostaSiscomex resposta = siscomex.send(
                RequisicaoSiscomex.get("/duimp-api/api/ext/duimp/21BR00000012345/1"));

        System.out.println(resposta.status() + " " + resposta.corpo());
    }
}
```

Você não precisa autenticar manualmente. A primeira chamada obtém os tokens; as seguintes reaproveitam a sessão.

---

## Conceitos

| Peça | O que é | Onde fica |
|---|---|---|
| `Siscomex` | Fachada. Uma instância por ambiente e credencial. Envia requisições e autentica sob demanda. | `com.souhenriza` |
| Credenciais | Como você prova quem é: certificado A1 ou chave de acesso. | `autenticacao.credencial` |
| `GerenciadorSessao` | Guarda os tokens e decide quando renovar. | `autenticacao.sessao` |
| Serviços (`DuimpClient`, `DueClient`) | Métodos de negócio sobre o `Siscomex`. | `duimp`, `due` |
| `ExcecaoSiscomex` | Falha de autenticação ou de comunicação. | `autenticacao.erro` |
| `PortalException` | Erro de negócio devolvido pelo portal (usado pelos serviços). | `shared` |

Fluxo de uma chamada:

```
seu código → Siscomex.send → GerenciadorSessao (obtém/renova tokens) → portal
                                                                      ↓
seu código ← RespostaSiscomex ←──────────────────────────────────── resposta
```

---

## Ambientes

O enum `com.souhenriza.Ambiente` traz as URLs base:

| Constante | URL | Observação |
|---|---|---|
| `PRODUCAO` | `https://portalunico.siscomex.gov.br/` | Dados reais. |
| `VALIDACAO` | `https://val.portalunico.siscomex.gov.br/` | Tem manutenções de segunda a sexta, das 18h às 07h, e nos fins de semana. A biblioteca não modela essa janela. |
| `HOMOLOGACAO` | `https://hom.pucomex.serpro.gov.br/` | Ambiente de testes. |

`ambiente.getUrl()` devolve a URL. Toda URL precisa ser HTTPS; caso contrário a criação do autenticador falha.

---

## Credenciais

Há dois tipos de credencial e um armazém de confiança opcional.

### 1. Certificado A1 (e-CPF ou e-CNPJ)

```java
CertificadoA1 certificado = CertificadoA1.deArquivo(Path.of("certificado.pfx"), senha, PerfilAcesso.IMPEXP);
```

Ao carregar o `.pfx`, a biblioteca confere, **antes de qualquer chamada de rede**:

- a validade do certificado;
- se o arquivo tem uma única chave privada;
- se o tipo do certificado (e-CPF ou e-CNPJ) é aceito pelo perfil informado.

A senha é um `char[]` e é zerada ao final da leitura. Arquivo vencido, senha incorreta, chave ausente ou tipo não aceito geram `ExcecaoSiscomex` com a causa explicada na mensagem.

Se você já tem um `SSLContext` pronto, pode usar o construtor `new CertificadoA1(contextoSsl, perfil)`.

### 2. Chave de acesso (sem certificado)

O portal permite gerar, no próprio portal, um par `Client-Id` e `Client-Secret` para autenticar sem o certificado A1:

```java
Credenciais chaves = ChavesDeAcesso.deChaveDeAcesso("client-id", "client-secret", PerfilAcesso.AGECARGA);
Siscomex siscomex = new Siscomex(Environment.VALIDACAO, chaves);
```

Detalhes:

- O par vai nos headers de `POST /portal/api/autenticar/chave-acesso`. Não há certificado de cliente; a conexão usa a configuração TLS padrão da JVM.
- O `toString` mostra o `Client-Id` e nunca o `Client-Secret`.
- `Client-Id` e `Client-Secret` não podem ser vazios (`IllegalArgumentException`) nem nulos (`NullPointerException`).
- O segredo fica em `String`, que o Java não consegue apagar da memória. Guarde-o em variável de ambiente ou cofre, não no código.

### Perfis de acesso (`PerfilAcesso`)

O perfil define o papel com que você acessa o portal. Ele precisa ser compatível com o tipo do seu certificado.

| Perfil | Descrição | e-CPF | e-CNPJ |
|---|---|:---:|:---:|
| `AGECARGA` | Agente de Carga | ✔ | |
| `AGEREMESS` | Remessa Expressa/Correio | ✔ | ✔ |
| `AJUDESPAC` | Ajudante de Despachante | ✔ | |
| `CONTATOOEA` | Ponto de Contato OEA | ✔ | |
| `DEPOSIT` | Depositário | ✔ | ✔ |
| `EMPRCOMEL` | Empresa de Comércio Eletrônico | ✔ | ✔ |
| `HABILITAD` | Habilitador | ✔ | |
| `IMPEXP` | Declarante Importador/Exportador | ✔ | |
| `INSTFINANC` | Instituição Financeira | ✔ | |
| `OPERPORT` | Operador Portuário | ✔ | ✔ |
| `RESPLEGAL` | Responsável Legal OEA | ✔ | |
| `TRANSPEST` | PF – Representante de TETI | ✔ | |
| `TRANSPORT` | Transportador | ✔ | ✔ |
| `TERCEIROS` | Terceiros (outros intervenientes) | | ✔ |

`PerfilAcesso.de("IMPEXP")` converte um texto de configuração. Um valor vazio ou desconhecido gera `ExcecaoSiscomex` (código `PUCX-ER0101` ou `PUCX-ER0102`).

Chave de acesso não passa por essa validação de certificado: quem recusa o perfil é o portal.

### 3. Armazém de confiança do servidor (`TrustStore`)

Por padrão, a conexão com o portal valida o certificado do servidor com o armazém da JVM (`TrustStore.padrao()`). Para usar um conjunto próprio de autoridades, informe um arquivo PKCS#12:

```java
TrustStore confianca = TrustStore.deArquivo(Path.of("truststore.p12"), senhaDoTrustStore);
CertificadoA1 certificado = CertificadoA1.deArquivo(Path.of("certificado.pfx"), senha, PerfilAcesso.IMPEXP, confianca);
```

Para criar o arquivo, importe as autoridades com o `keytool`:

```bash
keytool -importcert -noprompt -alias ac-raiz -file ac-raiz.pem \
        -keystore truststore.p12 -storetype PKCS12 -storepass <senha>
```

Um arquivo sem nenhum certificado confiável é recusado ao ser carregado.

> A classe `ArmazemDeConfiancaICPBrasil` está marcada como `@Deprecated`. Ela lê uma pasta de autoridades, mas não é usada pela autenticação. Não a use em código novo.

---

## Sessão e tokens

Os tokens do portal (JWT e CSRF) são tratados pelo `GerenciadorSessao`:

| Regra | Comportamento |
|---|---|
| **Obtenção** | Na primeira chamada, ou quando ainda não há sessão. |
| **Validade** | A sessão é reaproveitada por até **55 minutos**. O JWT vence em 60. |
| **CSRF** | Cada resposta pode trazer um novo `X-CSRF-Token`, que substitui o anterior. |
| **Sessão rejeitada** | Se o portal responder **401**, a biblioteca autentica de novo e repete a chamada **uma vez**. Se o 401 persistir, lança `ExcecaoSiscomex`. |
| **Intervalo** | O portal aceita uma autenticação por minuto (PLAT-ER2033). A biblioteca espera esse intervalo sozinha. |
| **Concorrência** | Chamadas simultâneas esperam uma única autenticação; não há tempestade de logins. |

Para inspecionar a sessão, use `siscomex.getSessionManagement()`, que devolve o `GerenciadorSessao`. Ele é útil para diagnóstico; em uso normal você não precisa dele.

Os tokens nunca aparecem em `toString`, nem em logs.

---

## Requisições genéricas

Use `Siscomex.send` para chamar qualquer endpoint do portal:

```java
RequisicaoSiscomex consulta = RequisicaoSiscomex.get("/duimp-api/api/ext/duimp/21BR00000012345/1");
RequisicaoSiscomex envio    = RequisicaoSiscomex.post("/duimp-api/api/ext/duimp", corpoJson);
RequisicaoSiscomex alteracao = RequisicaoSiscomex.put("/duimp-api/api/ext/duimp/21BR00000012345/1", corpoJson);
RequisicaoSiscomex exclusao = RequisicaoSiscomex.delete("/duimp-api/api/ext/duimp/21BR00000012345/1");

RespostaSiscomex resposta = siscomex.send(consulta);

if (resposta.ehSucesso()) {                      // 2xx
    String corpo = resposta.corpo();
}
resposta.cabecalho("X-CSRF-Token");              // Optional<String>
```

Regras:

- **Caminho**: começa com `/` e é relativo ao host. **Inclua a base URL do serviço** (`/duimp-api/...`, `/due/...`). A biblioteca não conhece a base de cada API.
- **Headers**: `Authorization` (JWT) e `X-CSRF-Token` são acrescentados automaticamente.
- **Corpo**: é enviado como texto. A serialização JSON fica com quem chama.
- **Erros de negócio**: respostas que não são 401 (403, 404, 422, 500...) **não geram exceção**. Voltam com status e corpo, porque o significado delas depende do serviço.
- **Caminho montado com dados externos**: os segmentos não são codificados pela biblioteca. Valide os valores antes de montar o caminho, como os serviços abaixo fazem.

---

## Serviço DUIMP

`DuimpClient` expõe os métodos da DUIMP (Declaração Única de Importação):

```java
DuimpClient duimp = new DuimpClient(siscomex);

NumeroDuimp numero = NumeroDuimp.of("21BR00000012345");
VersaoDuimp versao = VersaoDuimp.of(2);

VersaoVigente vigente = duimp.consultarVersaoVigente(numero);
DuimpDados dados      = duimp.consultarDados(numero, versao);
List<ItemDuimp> itens = duimp.consultarItens(numero, versao, IntervaloItens.padrao());
```

| Método | Faz |
|---|---|
| `consultarVersaoVigente(numero)` | Versão vigente da DUIMP. |
| `consultarDados(numero, versao)` | Dados gerais (capa). |
| `consultarValoresCalculados(numero, versao)` | Valores calculados da DUIMP. |
| `consultarItens(numero, versao, intervalo)` | Itens de uma faixa. |
| `consultarItem(numero, versao, item)` | Um item. |
| `consultarValoresCalculadosItem(numero, versao, item)` | Valores calculados de um item. |
| `consultarDiagnostico(numero, versao)` | Diagnóstico da DUIMP. |
| `consultarChavesAcesso(numeros)` | Chaves de acesso de uma lista de DUIMPs. |
| `consultarChavesAcessoPorImportador(ni, inicio, fim, offset, limit)` | Chaves de acesso por importador e período. |
| `salvarDadosGerais(duimp)` | **Envio** (POST) da capa. Valida localmente antes. |

Os objetos de valor validam a entrada na criação, antes de qualquer chamada:

| Tipo | Regra |
|---|---|
| `NumeroDuimp.of(String)` | Formato do número da DUIMP. |
| `VersaoDuimp.of(int)` | De 0 a 9999. |
| `NumeroItem.of(int)` | Número do item. |
| `IntervaloItens.padrao()` | Primeira página, com até 100 itens. |

Valor inválido lança `DuimpException`.

`salvarDadosGerais` roda `DuimpValidator.validar` antes do envio. Se a capa não passar, lança `DuimpValidacaoException` (subclasse de `DuimpException`) e **nada é enviado**.

O portal exige o perfil `IMPEXP` para a DUIMP. A biblioteca não verifica isso antes da chamada; é o portal que recusa.

---

## Serviço DUE

`DueClient` expõe consultas da DUE (Declaração Única de Exportação):

```java
DueClient due = new DueClient(siscomex);

DueDados porNumero = due.consultarDadosPorNumeroDue(NumeroDue.of("..."));
DueDados porRuc    = due.consultarDadosPorNumeroRUC("...");
```

| Método | Faz |
|---|---|
| `consultarLinkDues(chaveDeAcessoNFe)` | Links de DUEs associadas a uma chave de NF-e. |
| `consultarDadosResumidosDue(numeroDue, ruc)` | Dados resumidos. |
| `consultarDadosPorNumeroDue(numeroDue)` | Dados completos pelo número da DUE. |
| `consultarDadosPorNumeroRUC(ruc)` | Dados completos pelo RUC. |
| `consultarAtosConcessoriosIsencao(numeroDue, item, ...)` | Atos concessórios de isenção. |
| `consultarAtosConcessionariosSuspensao(numeroDue, ...)` | Atos concessórios de suspensão. |
| `exigenciasFiscais(numeroDue)` | Exigências fiscais da DUE. |

O RUC é validado pelo formato `\d[A-Z]{2}\d{32}` antes da chamada. `NumeroDue.of` valida o número da DUE (14 caracteres, formato `NNAANNNNNNNNNN`). Valor inválido lança `DueException`.

Todos os métodos são somente leitura.

---

## Erros

Há duas famílias de exceção. Saiba qual esperar em cada camada:

| Exceção | Quando | Principais métodos |
|---|---|---|
| `ExcecaoSiscomex` (`RuntimeException`) | Autenticação, comunicação, TLS, parada programada. | `codigoErroPortal()`, `tempoEspera()`, `ehRepetivel()`, `erroResposta()` |
| `PortalException` (`RuntimeException`) | Erro de negócio devolvido pelo portal em um serviço. Base de `DuimpException` e `DueException`. | `status()`, `codigoInterno()`, `erros()`, `isNotFound()`, `isBusinessError()`, `ehRepetivel()` |

```java
try {
    duimp.consultarDados(numero, versao);
} catch (ExcecaoSiscomex e) {
    // problema de sessão, rede ou TLS
    e.codigoErroPortal();   // Optional<CodigoErroPortal>, por exemplo PLAT-ER2033
    e.tempoEspera();        // Optional<Duration>: quanto esperar antes de tentar de novo
    e.ehRepetivel();        // true para falhas de rede, timeout e 5xx
} catch (DuimpException e) {
    // erro de negócio do portal
    e.status();
    e.codigoInterno();
    e.erros();
}
```

### Códigos conhecidos (`CodigoErroPortal`)

| Código | Constante | Significado |
|---|---|---|
| `PUCX-ER0101` | `CABECALHO_PERFIL_AUSENTE` | Header `Role-Type` não enviado. |
| `PUCX-ER0102` | `CABECALHO_PERFIL_INVALIDO` | Header `Role-Type` inválido. |
| `PUCX-ER0401` | `PARADA_PROGRAMADA` | Portal em parada, entre 01:00 e 03:00. Repetível. |
| `PLAT-ER2001` | `HANDSHAKE_MTLS_FALHOU` | Certificado não identificado no handshake mTLS. |
| `PLAT-ER2008` | `ECNPJ_NAO_PERMITIDO_PARA_PERFIL` | e-CNPJ usado com perfil que exige e-CPF. |
| `PLAT-ER2013` | `SEM_REPRESENTACAO_NO_PERFIL` | Usuário sem representações no perfil. |
| `PLAT-ER2023` | `CNPJ_NAO_HABILITADO_PARA_PERFIL` | CNPJ sem habilitação no perfil. |
| `PLAT-ER2033` | `LIMITE_AUTENTICACAO` | Nova autenticação em menos de 60 s. Repetível após 60 s. |
| `PLAT-ER3005` | `CPF_IRREGULAR` | CPF do usuário irregular. |
| `PLAT-ER8003` | `CHAVE_ACESSO_INVALIDA` | Chave de acesso inválida. |
| `PLAT-ER8008` | `AUTENTICACAO_CHAVE_ACESSO_FALHOU` | Falha na autenticação por chave de acesso. |

`PLAT-ER8001` e `PLAT-ER8002` (certificado de equipamento) estão mapeados, mas não são usados por esta biblioteca.

Erros de rede e TLS:

- Falha de conexão ou timeout: `ExcecaoSiscomex` com `ehRepetivel() == true`, e a exceção original em `getCause()`.
- Falha no handshake TLS: `ExcecaoSiscomex` com mensagem começando por `Falha TLS`, `ehRepetivel() == false`, e `SSLException` na causa. Em geral é um certificado de servidor não confiável ou uma configuração de truststore errada.

---

## Janela de parada programada

Entre **01:00 e 03:00** (horário de Brasília, `America/Sao_Paulo`), o portal fica indisponível.

Nesse período, a biblioteca **recusa a chamada sem contactar o portal** e lança `ExcecaoSiscomex`:

- `codigoErroPortal()` = `PARADA_PROGRAMADA`;
- `tempoEspera()` = o tempo que falta até as 03:00.

Use `tempoEspera()` para agendar a nova tentativa.

---

## Logs

A biblioteca usa SLF4J. Adicione uma implementação ao seu projeto, por exemplo:

```xml
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-simple</artifactId>
    <version>2.0.20</version>
</dependency>
```

O que vai para o log:

- tokens (JWT e CSRF) **nunca**;
- conteúdo de certificado **nunca**;
- a query string dos caminhos é removida, e o NI do importador em `/chaves-acesso/importadores/{ni}` é trocado por `***`.

Atenção: o log da biblioteca é seguro, mas **os seus** logs de objetos de negócio não são. Veja [Segurança](#segurança-o-que-você-deve-fazer).

---

## Testes e CI

```bash
mvn -o test     # suíte de testes, sem rede (291 testes)
mvn verify      # testes + análise de vulnerabilidades (OWASP Dependency-Check)
```

- Os certificados de teste em `src/test/resources/certificates` são **fictícios**. Senha: `senha`. A PKI de teste do HTTPS usa a senha `confianca` nos truststores.
- Os testes não usam o portal real. Para validar contra homologação, use um certificado A1 seu com o perfil habilitado.
- O `mvn verify` precisa da variável `NVD_API_KEY` para consultar a base do NVD sem limite de requisições.
- A integração contínua (`.github/workflows/ci.yaml`) roda em cada push e pull request para `main`: detecção de segredos com GitLeaks (histórico completo), testes unitários e análise de dependências.

---

## Segurança: o que você deve fazer

- **Não versione** certificados reais, chaves privadas ou senhas. O `.gitignore` já ignora `.pfx`, `.p12` (exceto os de teste), `.jks`, `.key`, `.pem`, `.env` e arquivos de segredo.
- **Senha do certificado** em `char[]`, vinda de variável de ambiente ou cofre. Não como literal.
- **Segredo da chave de acesso** também fora do código-fonte.
- **Respeite o limite** de uma autenticação por minuto. Não faça um login por requisição; deixe o `Siscomex` reaproveitar a sessão.
- **Não registre objetos de negócio** em log. Alguns records (`ImportadorCover`, `ChaveDeAcessoNFe`, `RequisicaoSiscomex`) ainda mostram o NI, CPF/CNPJ ou o corpo no `toString` padrão. Logue só o que você precisa, sem o objeto inteiro.
- **Monte caminhos só com valores validados.** Use os value objects dos serviços.

---

## Escopo e limitações

**Implementado**

- Autenticação por certificado A1 (mTLS), com validação de validade, tipo e perfil.
- Autenticação por chave de acesso (`Client-Id` / `Client-Secret`).
- Armazém de confiança do servidor (`TrustStore`).
- Sessão com renovação de CSRF, re-autenticação em 401 e intervalo mínimo entre autenticações.
- Envio genérico de requisições autenticadas para qualquer serviço do portal.
- Recusa local durante a janela de parada programada.
- Serviço DUIMP (consultas e envio da capa) e serviço DUE (consultas).

**Não implementado**

- **Certificado A3** (PKCS#11, token ou cartão).
- **Decodificação do JWT** para ler as informações do usuário.
- **Validação da cadeia do certificado do cliente** contra as autoridades ICP-Brasil.
- **Verificação de perfil nos serviços.** Por exemplo, a DUIMP exige `IMPEXP`; quem recusa é o portal.
- **Autenticação de sistema integrado.** Foi removida: é uso exclusivo da administração pública.
- **Serviços além de DUIMP e DUE.** Para outro serviço, use `Siscomex.send` com `RequisicaoSiscomex`.

---

## Organização do código

| Pacote | Conteúdo |
|---|---|
| `com.souhenriza` | `Siscomex` (fachada) e `Ambiente` (ambientes). |
| `com.souhenriza.autenticacao` | Autenticação: `AutenticadorHTTP`, `PerfilAcesso`, `JanelaDeManutencao`, `UrlPortal`. |
| `…autenticacao.credencial` | `Credenciais`, `CertificadoA1`, `ChavesDeAcesso`, `TrustStore`. |
| `…autenticacao.certificado` | Leitura do tipo do certificado ICP-Brasil (e-CPF / e-CNPJ). |
| `…autenticacao.sessao` | `GerenciadorSessao`, `Sessao`, `Tokens`, `Pausa`. |
| `…autenticacao.api` | `RequisicaoSiscomex`, `RespostaSiscomex`, `ClienteApi`. |
| `…autenticacao.erro` | `ExcecaoSiscomex`, `CodigoErroPortal`, `FalhasPortal`. |
| `com.souhenriza.http` | Transporte HTTP (`PortalHttp`, `PortalRequest`, `PortalResponse`). Uso interno. |
| `com.souhenriza.shared` | Base dos serviços: `PortalClient`, `SiscomexGateway`, `PortalException`, `Query`. |
| `com.souhenriza.duimp` | Serviço DUIMP: `DuimpClient`, `DuimpValidator`, modelos. |
| `com.souhenriza.due` | Serviço DUE: `DueClient`, modelos. |

Para criar um novo serviço, siga o padrão de `DueClient`: receba um `Siscomex` no construtor, valide os valores de entrada com value objects e use `PortalClient` para converter as respostas.

---

## Licença

Apache License 2.0, conforme declarado no `pom.xml` e constante no arquivo 'LICENSE'.

---

*Esta é uma biblioteca não oficial. Ela não é mantida pelo Siscomex, pela Receita Federal ou pelo SERPRO.*
