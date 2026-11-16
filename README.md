# Siscomex

Biblioteca Java para autenticação e comunicação com os serviços do Portal Único de Comércio Exterior (Siscomex).

A biblioteca cuida da parte mais complexa das chamadas: autenticação mTLS com certificado digital, obtenção e renovação de tokens de sessão, controle da janela de parada programada e tratamento dos erros do portal. Ela não implementa nenhum serviço de negócio. Cada serviço (DUIMP, DUE, etc.) é uma biblioteca separada que usa a `siscomex` para enviar requisições autenticadas.

## Requisitos

- Java 21 ou superior
- Maven

## Instalação

A biblioteca ainda não está publicada em um repositório. Para usá-la localmente, instale no seu repositório Maven:

```bash
mvn install
```

e declare a dependência no seu projeto:

```xml
<dependency>
    <groupId>com.souhenriza</groupId>
    <artifactId>siscomex</artifactId>
    <version>1.0</version>
</dependency>
```

## Uso rápido

```java
import com.souhenriza.Environment;
import com.souhenriza.Siscomex;
import com.souhenriza.auth.api.SiscomexRequest;
import com.souhenriza.auth.api.SiscomexResponse;
import com.souhenriza.auth.RoleType;
import com.souhenriza.auth.credential.A1Certificate;

import java.nio.file.Path;

public class Exemplo {

    public static void main(String[] args) {
        // Lida de uma variável de ambiente para não ficar no código-fonte.
        char[] senha = System.getenv("SISCOMEX_PFX_SENHA").toCharArray();

        A1Certificate certificado = A1Certificate.from(Path.of("certificado.pfx"), senha, RoleType.IMPEXP);
        Siscomex siscomex = new Siscomex(Environment.HOMOLOGACAO, certificado);

        SiscomexResponse resposta = siscomex.send(
                SiscomexRequest.get("/duimp-api/api/ext/duimp/21BR00000012345/1"));

        System.out.println(resposta.status() + " " + resposta.body());
    }
}
```

Você não precisa chamar a autenticação manualmente. A primeira chamada a `send` obtém os tokens, e as seguintes reaproveitam a sessão.

## Ambientes

O enum `com.souhenriza.Environment` traz as URLs principais:

| Constante | URL |
|---|---|
| `PRODUCAO` | `https://portalunico.siscomex.gov.br/` |
| `VALIDACAO` | `https://val.portalunico.siscomex.gov.br/` |
| `HOMOLOGACAO` | `https://hom.pucomex.serpro.gov.br/` |

O ambiente de validação tem manutenções de segunda a sexta, das 18h às 07h, e nos fins de semana. Essa janela não é modelada pela biblioteca.

## Credenciais

### Certificado A1 (e-CPF ou e-CNPJ)

```java
A1Certificate certificado = A1Certificate.from(Path.of("certificado.pfx"), senha, RoleType.IMPEXP);
```

Ao carregar o `.pfx`, a biblioteca:

- confere a validade do certificado;
- confere se o arquivo tem uma única chave privada;
- confere se o tipo do certificado (e-CPF ou e-CNPJ) é aceito pelo perfil informado, antes de qualquer chamada de rede.

A senha é zerada na memória ao final da leitura. Arquivos vencidos, com senha incorreta ou com mais de uma chave geram `SiscomexException` com mensagem explicando o motivo.

O perfil (`RoleType`) define quais tipos de certificado são aceitos. Use `RoleType.from("IMPEXP")` se o perfil vier de um texto de configuração.

### TrustStore do servidor

Por padrão, a conexão com o portal valida o certificado do servidor com o TrustStore da JVM. Para usar um conjunto próprio de autoridades (por exemplo, a cadeia ICP-Brasil ou a do SERPRO), informe um arquivo PKCS#12:

```java
TrustStore confianca = TrustStore.from(Path.of("truststore.p12"), senhaDoTrustStore);
A1Certificate certificado = A1Certificate.from(Path.of("certificado.pfx"), senha, RoleType.IMPEXP, confianca);
```

Para criar o arquivo, importe as autoridades com o `keytool`:

```bash
keytool -importcert -noprompt -alias ac-raiz -file ac-raiz.pem \
        -keystore truststore.p12 -storetype PKCS12 -storepass <senha>
```

Um arquivo sem nenhum certificado confiável é recusado ao ser carregado.

## Sessão e tokens

Os tokens do portal (JWT e CSRF) são tratados pela biblioteca:

- **Obtenção**: feita na primeira chamada, ou quando a sessão ainda não existe.
- **Validade**: a sessão é reaproveitada por até 55 minutos, antes do vencimento de 60 minutos do JWT.
- **Renovação do CSRF**: o valor devolvido em cada resposta substitui o anterior.
- **Sessão rejeitada**: quando o portal responde 401, a biblioteca autentica de novo e repete a chamada uma vez. Se o 401 persistir, lança `SiscomexException`.
- **Intervalo entre autenticações**: o portal limita novas autenticações a uma por minuto. A biblioteca espera esse intervalo automaticamente.

Para inspecionar a sessão atual, use `siscomex.getSessionManagement()`.

## Enviando requisições

`Siscomex.send` recebe uma `SiscomexRequest` e devolve uma `SiscomexResponse`:

```java
SiscomexRequest consulta = SiscomexRequest.get("/duimp-api/api/ext/duimp/21BR00000012345/1");
SiscomexRequest envio = SiscomexRequest.post("/duimp-api/api/ext/duimp", "{\"numero\":\"21BR00000012345\"}");
SiscomexRequest alteracao = SiscomexRequest.put("/duimp-api/api/ext/duimp/21BR00000012345/1", corpoJson);
SiscomexRequest exclusao = SiscomexRequest.delete("/duimp-api/api/ext/duimp/21BR00000012345/1");

SiscomexResponse resposta = siscomex.send(consulta);
if (resposta.isSuccess()) {
    String corpo = resposta.body();
}
resposta.header("X-CSRF-Token");
```

- O `path` é o caminho a partir do host, **incluindo a Base URL do serviço**. A biblioteca não sabe qual é a Base URL de cada API; cada lib de serviço define as suas.
- Os headers `Authorization` (JWT) e `X-CSRF-Token` são acrescentados automaticamente.
- O corpo JSON é enviado como texto. A serialização fica por conta de quem chama.
- Respostas diferentes de 401 (inclusive 403, 404, 422 e 500) **não geram exceção**. Elas voltam para quem chamou, com status e corpo, porque o significado dos erros de negócio depende de cada serviço.

## Erros

Toda falha da biblioteca é lançada como `SiscomexException` (uma `RuntimeException`):

```java
try {
    siscomex.send(requisicao);
} catch (SiscomexException e) {
    e.getMessage();
    e.portalErrorCode();   // Optional<PortalErrorCode>, código como PLAT-ER2033
    e.retryAfter();        // Optional<Duration>, quanto esperar antes de tentar de novo
    e.isRetryable();       // true para falhas de rede e timeouts
    e.getCause();          // exceção original, quando houver
}
```

Os códigos conhecidos do portal estão no enum `PortalErrorCode`.

### Janela de parada programada

Entre 01:00 e 03:00 (horário de Brasília), o portal fica indisponível. Nesse período, `send` recusa a chamada **sem contactar o portal** e lança `SiscomexException` com `portalErrorCode()` igual a `MAINTENANCE_WINDOW` e `retryAfter()` com o tempo que falta.

## Logs

A biblioteca usa SLF4J. Ela não traz uma implementação de log. Adicione uma no seu projeto, por exemplo:

```xml
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-simple</artifactId>
    <version>2.0.20</version>
</dependency>
```

Os logs nunca incluem tokens (JWT ou CSRF) nem o conteúdo do certificado.

## Escopo atual

Implementado:

- Autenticação por certificado A1 (mTLS), com validação de tipo, validade e perfil.
- Sessão com renovação de CSRF, re-autenticação em 401 e intervalo mínimo entre autenticações.
- Envio de requisições autenticadas para qualquer serviço do portal.
- Recusa local durante a janela de parada programada.

Ainda não implementado:

- **Chave de acesso** como forma de autenticação. Hoje só o certificado A1 é suportado.
- **Certificado A3** (PKCS#11, token ou cartão).
- **Decodificação do JWT** para ler as informações do usuário.
- **Validação da cadeia do certificado do cliente** contra as autoridades ICP-Brasil. A pasta de autoridades pode ser lida com `ICPBrasilTrustStore`, mas ainda não está ligada ao `TrustStore` usado pela autenticação.
- **Validação do perfil** nos serviços. Por exemplo, a DUIMP exige o perfil `IMPEXP`, mas a biblioteca não verifica isso antes de enviar a chamada. É o portal que recusa.

## Criando uma biblioteca de serviço

Um serviço expõe métodos de negócio sobre o `Siscomex.send`. O exemplo real é o pacote `com.souhenriza.duimp`, com a `DuimpClient` (somente consultas, por enquanto):

```java
DuimpClient duimp = new DuimpClient(siscomex);

DuimpDados dados = duimp.consultarDados(NumeroDuimp.of("21BR00000012345"), VersaoDuimp.of(2));
List<ItemDuimp> itens = duimp.consultarItens(NumeroDuimp.of("21BR00000012345"), VersaoDuimp.of(2), IntervaloItens.padrao());
```

- Os números (DUIMP, versão, item, faixa) são validados ao criar os objetos, antes de qualquer caminho chegar à URL.
- Erros de negócio do portal (404, 422, 5xx) viram `DuimpException`, com `codigoInterno()` e `erros()`.
- Falhas de autenticação e de comunicação continuam como `SiscomexException`.

## Testes

```bash
mvn -o test
```

Os testes usam certificados de teste em `src/test/resources/certificates`. Todos são fictícios e autoassinados (ou emitidos por uma CA de teste), com senha `senha`. A PKI do teste HTTPS usa a senha `confianca` nos truststores.

Os testes não usam o portal real. Para validar contra homologação, é preciso um certificado A1 seu com o perfil habilitado.

## Segurança

- Não versione certificados reais, chaves privadas nem senhas. O `.gitignore` já ignora `.pfx`, `.p12` (exceto os de teste), `.key`, `.pem` e arquivos de segredo.
- Passe a senha do certificado como `char[]`, não como `String`, e não a deixe em código-fonte.
