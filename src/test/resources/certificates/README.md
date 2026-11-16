# Certificados de teste

Arquivos PKCS#12 usados pelos testes unitários. Todos têm a senha `senha` e são autoassinados.
Os identificadores (nomes, CPF e CNPJ) são fictícios e não correspondem a pessoas reais.

| Arquivo | Conteúdo SAN | Resultado esperado |
|---|---|---|
| `ecpf.p12` | nome + CPF (`2.16.76.1.3.4`) | e-CPF |
| `ecnpj.p12` | nome + CNPJ (`2.16.76.1.3.3`) | e-CNPJ |
| `ecnpj_responsible_cpf_first.p12` | CPF do responsável antes do CNPJ | e-CNPJ |
| `ecpf_short_cpf.p12` | CPF com 10 dígitos | não identificado |
| `name_only.p12` | apenas nome | não identificado |
| `no_san.p12` | sem extensão SAN | não identificado |
| `ecpf_certificate_only.p12` | e-CPF sem chave privada | sem chave privada |
| `ecpf_expired.p12` | e-CPF com validade até 01/01/2021 | vencido |
| `ecpf_not_yet_valid.p12` | e-CPF com início em 01/01/2099 | ainda não válido |

Gerados com OpenSSL 3, a partir de configurações com `otherName.N = <OID>;OCTETSTRING:<valor>`.

Os arquivos `tls/` são a PKI de teste do handshake HTTPS: `server.p12` (servidor para `localhost`, emitido pela CA de teste), `ca-truststore.p12` (somente a CA de teste) e `other-ca-truststore.p12` (outra CA, que não emitiu o servidor). Senha dos truststores: `confianca`.
