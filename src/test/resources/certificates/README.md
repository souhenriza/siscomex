# Certificados de teste

Arquivos PKCS#12 usados pelos testes unitários. Todos têm a senha `senha` e são autoassinados.
Os identificadores (nomes, CPF e CNPJ) são fictícios e não correspondem a pessoas reais.

Os campos `otherName` da SAN seguem o DOC-ICP-04: `2.16.76.1.3.1` traz os dados do titular do e-CPF
(data de nascimento `ddmmaaaa` + CPF + NIS + RG + órgão expedidor), `2.16.76.1.3.3` o CNPJ do e-CNPJ e
`2.16.76.1.3.2`/`2.16.76.1.3.4` o nome e os dados do responsável pelo e-CNPJ.

| Arquivo | Conteúdo SAN | Resultado esperado |
|---|---|---|
| `ecpf.p12` | dados do titular (`2.16.76.1.3.1`) + CEI zerado | e-CPF |
| `ecpf_printable_string.p12` | dados do titular como PrintableString | e-CPF |
| `ecnpj.p12` | responsável (`.3.2`, `.3.4`) + CNPJ (`.3.3`) + CEI | e-CNPJ |
| `ecnpj_responsible_cpf_first.p12` | dados do responsável antes do CNPJ | e-CNPJ |
| `ecnpj_alfanumerico.p12` | CNPJ alfanumérico | e-CNPJ |
| `ecpf_short_cpf.p12` | dados do titular com 18 posições (CPF incompleto) | não identificado |
| `ecpf_zero_cpf.p12` | dados do titular com CPF zerado | não identificado |
| `responsible_only.p12` | apenas dados do responsável (`.3.4`) | não identificado |
| `ecpf_legacy_layout.p12` | layout antigo e incorreto: 11 dígitos em `.3.4` | não identificado |
| `name_only.p12` | apenas nome do responsável (`.3.2`) | não identificado |
| `no_san.p12` | sem extensão SAN | não identificado |
| `ecpf_certificate_only.p12` | e-CPF sem chave privada | sem chave privada |
| `ecpf_expired.p12` | e-CPF com validade até 01/01/2021 | vencido |
| `ecpf_not_yet_valid.p12` | e-CPF com início em 01/01/2099 | ainda não válido |

Gerados com OpenSSL 3 pelo script `gerar.sh` (`./gerar.sh .`), que recria todos os arquivos acima.

Os arquivos `tls/` são a PKI de teste do handshake HTTPS: `server.p12` (servidor para `localhost`, emitido pela CA de teste), `ca-truststore.p12` (somente a CA de teste) e `other-ca-truststore.p12` (outra CA, que não emitiu o servidor). Senha dos truststores: `confianca`.
