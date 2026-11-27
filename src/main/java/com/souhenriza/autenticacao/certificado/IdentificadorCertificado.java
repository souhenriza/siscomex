package com.souhenriza.autenticacao.certificado;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1String;
import org.bouncycastle.asn1.ASN1TaggedObject;

import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Identifica o tipo de um certificado ICP-Brasil (e-CPF ou e-CNPJ) pelos campos {@code otherName} da extensão
 * Subject Alternative Name, conforme o DOC-ICP-04 (Requisitos Mínimos para as Políticas de Certificado na ICP-Brasil):
 * <ul>
 *   <li>e-CPF: {@code 2.16.76.1.3.1}, dados do titular: data de nascimento ({@code ddmmaaaa}, 8 posições),
 *       CPF (11 posições), NIS, RG e órgão expedidor.</li>
 *   <li>e-CNPJ: {@code 2.16.76.1.3.3}, CNPJ da empresa (14 posições). O e-CNPJ também traz
 *       {@code 2.16.76.1.3.2} (nome do responsável) e {@code 2.16.76.1.3.4} (dados do responsável, no mesmo
 *       formato do {@code 2.16.76.1.3.1}).</li>
 * </ul>
 */
public class IdentificadorCertificado {

    /** Tipo do GeneralName otherName na extensão SAN (RFC 5280). */
    private static final int TIPO_OUTRO_NOME = 0;

    /** Dados do titular pessoa física (e-CPF). */
    private static final String OID_DADOS_TITULAR_PF = "2.16.76.1.3.1";
    /** CNPJ da pessoa jurídica titular (e-CNPJ). */
    private static final String OID_CNPJ = "2.16.76.1.3.3";

    /** Posição do CPF dentro dos dados do titular: logo após a data de nascimento ({@code ddmmaaaa}). */
    private static final int POSICAO_CPF_INICIO = 8;
    private static final int POSICAO_CPF_FIM = POSICAO_CPF_INICIO + 11;

    private static final Pattern CPF = Pattern.compile("\\d{11}");
    /** CNPJ numérico ou alfanumérico: 12 posições de letras maiúsculas ou dígitos e 2 dígitos verificadores. */
    private static final Pattern CNPJ = Pattern.compile("[0-9A-Z]{12}\\d{2}");
    /** Campo não informado: o DOC-ICP-04 manda preencher com zeros. */
    private static final Pattern ZEROS = Pattern.compile("0+");

    /**
     * Construtor privado: a classe é um utilitário estático.
     */
    private IdentificadorCertificado() {
    }

    /**
     * Identifica se o certificado ICP-Brasil é e-CPF ou e-CNPJ a partir da extensão SAN.
     * <p>
     * O CNPJ tem precedência: um e-CNPJ carrega também os dados do responsável pessoa física.
     *
     * @param certificado certificado ICP-Brasil, não nulo
     * @return tipo do certificado (tipo não genérico)
     * @throws Exception                se a extensão SAN não puder ser lida
     * @throws IllegalArgumentException se o certificado não trouxer CPF ou CNPJ válido nos campos ICP-Brasil
     */
    public static TipoCertificadoICPBrasil identificar(X509Certificate certificado) throws Exception {
        Collection<List<?>> nomesAlternativos = certificado.getSubjectAlternativeNames();
        if (nomesAlternativos == null) {
            throw identificadorNaoEncontrado();
        }

        boolean temCpf = false;

        for (List<?> entrada : nomesAlternativos) {
            if (!ehOutroNome(entrada)) {
                continue;
            }

            OutroNome outroNome = lerOutroNome((byte[]) entrada.get(1));

            if (OID_CNPJ.equals(outroNome.oid()) && ehCnpj(outroNome.valor())) {
                return TipoCertificadoICPBrasil.ECNPJ;
            }

            if (OID_DADOS_TITULAR_PF.equals(outroNome.oid()) && cpfTitular(outroNome.valor()) != null) {
                temCpf = true;
            }
        }

        if (temCpf) {
            return TipoCertificadoICPBrasil.ECPF;
        }

        throw identificadorNaoEncontrado();
    }

    /**
     * Indica se o valor tem formato de CNPJ e não é composto só de zeros.
     *
     * @param valor texto do campo CNPJ
     * @return {@code true} se for um CNPJ válido em formato (tipo não genérico)
     */
    private static boolean ehCnpj(String valor) {
        return CNPJ.matcher(valor).matches() && !ZEROS.matcher(valor).matches();
    }

    /**
     * CPF do titular nos dados {@code 2.16.76.1.3.1}.
     *
     * @param dadosTitular texto dos dados do titular
     * @return CPF com 11 dígitos, ou {@code null} se ausente, curto demais ou zerado (tipo não genérico)
     */
    private static String cpfTitular(String dadosTitular) {
        if (dadosTitular.length() < POSICAO_CPF_FIM) {
            return null;
        }
        String cpf = dadosTitular.substring(POSICAO_CPF_INICIO, POSICAO_CPF_FIM);
        if (!CPF.matcher(cpf).matches() || ZEROS.matcher(cpf).matches()) {
            return null;
        }
        return cpf;
    }

    /**
     * Indica se a entrada da extensão SAN é do tipo otherName e tem o formato esperado.
     *
     * @param entrada entrada da lista SAN (tipo, valor)
     * @return {@code true} se for um otherName com valor em bytes (tipo não genérico)
     */
    private static boolean ehOutroNome(List<?> entrada) {
        return entrada.size() >= 2
                && entrada.get(0) instanceof Integer
                && (Integer) entrada.get(0) == TIPO_OUTRO_NOME
                && entrada.get(1) instanceof byte[];
    }

    /**
     * Lê o DER de um OtherName: SEQUENCE { type-id OID, value [0] EXPLICIT ANY }. O DOC-ICP-04 define o valor como
     * OCTET STRING, mas há emissores que usam PrintableString ou UTF8String; os dois formatos são aceitos.
     *
     * @param der bytes codificados em DER
     * @return OID e valor textual do otherName (tipo não genérico)
     */
    private static OutroNome lerOutroNome(byte[] der) {
        ASN1Sequence sequencia = ASN1Sequence.getInstance(der);
        String oid = ASN1ObjectIdentifier.getInstance(sequencia.getObjectAt(0)).getId();
        ASN1TaggedObject marcado = ASN1TaggedObject.getInstance(sequencia.getObjectAt(1));
        ASN1Encodable base = marcado.getBaseObject();

        String valor;
        if (base instanceof ASN1OctetString octetos) {
            valor = new String(octetos.getOctets(), StandardCharsets.ISO_8859_1);
        } else if (base instanceof ASN1String texto) {
            valor = texto.getString();
        } else {
            valor = "";
        }

        return new OutroNome(oid, valor.trim());
    }

    /**
     * Exceção padronizada para certificado sem CPF ou CNPJ identificável.
     *
     * @return exceção pronta para ser lançada (tipo não genérico)
     */
    private static IllegalArgumentException identificadorNaoEncontrado() {
        return new IllegalArgumentException("Não foi possível resgatar os valores de CPF ou CNPJ do certificado informado.");
    }

    /**
     * Par OID e valor de um campo otherName da extensão SAN.
     *
     * @param oid   identificador do campo, por exemplo {@code 2.16.76.1.3.1}
     * @param valor conteúdo textual do campo
     */
    private record OutroNome(String oid, String valor) {
    }
}
