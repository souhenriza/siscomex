#!/usr/bin/env bash
# Gera os .p12 de teste com otherName no layout do DOC-ICP-04. Uso: gen.sh <diretorio-saida>
set -euo pipefail
OUT="$1"; W="$(mktemp -d)"; trap 'rm -rf "$W"' EXIT
PASS=senha
PF="01011990""12345678909""00000000000""000000000000000""000000"   # nasc + CPF + NIS + RG + órgão/UF
RESP="02021980""98765432100""00000000000""000000000000000""000000"

# $1=arquivo $2=CN $3=SAN (linhas otherName) [$4=startdate $5=enddate] [$6=nokey]
make() {
  local name="$1" cn="$2" san="$3" start="${4:-}" end="${5:-}" nokey="${6:-}"
  local d="$W/$name"; mkdir -p "$d"; : > "$d/index"; echo 01 > "$d/serial"
  cat > "$d/cnf" <<CNF
[req]
distinguished_name=dn
prompt=no
[dn]
CN=$cn
[ext]
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=clientAuth
CNF
  if [ -n "$san" ]; then printf 'subjectAltName=@san\n[san]\n%s\n' "$san" >> "$d/cnf"; fi
  cat >> "$d/cnf" <<CNF
[ca]
default_ca=c
[c]
database=$d/index
serial=$d/serial
new_certs_dir=$d
default_md=sha256
policy=p
copy_extensions=none
[p]
commonName=supplied
CNF
  openssl genrsa -out "$d/key" 2048 2>/dev/null
  if [ -n "$start" ]; then
    openssl req -new -key "$d/key" -config "$d/cnf" -out "$d/csr"
    openssl ca -batch -selfsign -keyfile "$d/key" -config "$d/cnf" -extensions ext \
      -startdate "$start" -enddate "$end" -in "$d/csr" -out "$d/crt" 2>/dev/null
  else
    openssl req -x509 -new -key "$d/key" -config "$d/cnf" -extensions ext -days 36500 -out "$d/crt"
  fi
  if [ -n "$nokey" ]; then
    openssl pkcs12 -export -nokeys -in "$d/crt" -name teste -passout pass:$PASS -out "$OUT/$name"
  else
    openssl pkcs12 -export -inkey "$d/key" -in "$d/crt" -name teste -passout pass:$PASS -out "$OUT/$name"
  fi
}

ECPF_SAN="otherName.0=2.16.76.1.3.1;OCTETSTRING:$PF
otherName.1=2.16.76.1.3.6;OCTETSTRING:000000000000"
make ecpf.p12 "PESSOA FICTICIA:12345678909" "$ECPF_SAN"
make ecpf_certificate_only.p12 "PESSOA FICTICIA:12345678909" "$ECPF_SAN" "" "" nokey
make ecpf_expired.p12 "PESSOA FICTICIA VENCIDA:12345678909" "$ECPF_SAN" 20200101000000Z 20210101000000Z
make ecpf_not_yet_valid.p12 "PESSOA FICTICIA FUTURA:12345678909" "$ECPF_SAN" 20990101000000Z 21000101000000Z
make ecpf_printable_string.p12 "PESSOA FICTICIA PRINTABLE:12345678909" \
  "otherName.0=2.16.76.1.3.1;PRINTABLESTRING:$PF"
make ecnpj.p12 "EMPRESA FICTICIA LTDA:12345678000195" \
  "otherName.0=2.16.76.1.3.2;OCTETSTRING:RESPONSAVEL FICTICIO
otherName.1=2.16.76.1.3.3;OCTETSTRING:12345678000195
otherName.2=2.16.76.1.3.4;OCTETSTRING:$RESP
otherName.3=2.16.76.1.3.7;OCTETSTRING:000000000000"
make ecnpj_responsible_cpf_first.p12 "EMPRESA FICTICIA RESPONSAVEL:12345678000195" \
  "otherName.0=2.16.76.1.3.4;OCTETSTRING:$RESP
otherName.1=2.16.76.1.3.3;OCTETSTRING:12345678000195"
make ecnpj_alfanumerico.p12 "EMPRESA FICTICIA ALFANUMERICA:12ABC34501DE35" \
  "otherName.0=2.16.76.1.3.3;OCTETSTRING:12ABC34501DE35
otherName.1=2.16.76.1.3.4;OCTETSTRING:$RESP"
make ecpf_short_cpf.p12 "TESTE CPF CURTO" "otherName.0=2.16.76.1.3.1;OCTETSTRING:010119901234567890"
make ecpf_zero_cpf.p12 "TESTE CPF ZERADO" \
  "otherName.0=2.16.76.1.3.1;OCTETSTRING:01011990000000000000000000000000000000000000000000000"
make ecpf_legacy_layout.p12 "TESTE LAYOUT ANTIGO" "otherName.0=2.16.76.1.3.4;OCTETSTRING:12345678909"
make responsible_only.p12 "TESTE SO RESPONSAVEL" "otherName.0=2.16.76.1.3.4;OCTETSTRING:$RESP"
make name_only.p12 "TESTE SO NOME" "otherName.0=2.16.76.1.3.2;OCTETSTRING:PF TESTE"
make no_san.p12 "TESTE SEM SAN" ""
