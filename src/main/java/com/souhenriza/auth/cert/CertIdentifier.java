package com.souhenriza.auth.cert;

import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1TaggedObject;

import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;

public class CertIdentifier {

    /** Tipo do GeneralName otherName na extensão SAN (RFC 5280). */
    private static final int SAN_TYPE_OTHER_NAME = 0;

    private static final String OID_CNPJ = "2.16.76.1.3.3";
    private static final String OID_CPF = "2.16.76.1.3.4";

    private static final int CNPJ_LENGTH = 14;
    private static final int CPF_LENGTH = 11;

    /**
     * Identifica se o certificado ICP-Brasil é e-CPF ou e-CNPJ a partir da extensão SAN.
     * <p>
     * Um e-CNPJ também pode conter o OID do CPF (do responsável), por isso o CNPJ
     * é verificado em todas as entradas antes de considerar o CPF.
     */
    public static ITIBrasilCertType identify(X509Certificate certificate) throws Exception {
        Collection<List<?>> alternativeNames = certificate.getSubjectAlternativeNames();
        if (alternativeNames == null) {
            throw noIdentifierFound();
        }

        boolean hasCpf = false;

        for (List<?> entry : alternativeNames) {
            if (!isOtherName(entry)) {
                continue;
            }

            OtherName otherName = parseOtherName((byte[]) entry.get(1));

            if (OID_CNPJ.equals(otherName.oid) && otherName.value.length() == CNPJ_LENGTH) {
                return ITIBrasilCertType.ECNPJ;
            }

            if (OID_CPF.equals(otherName.oid) && otherName.value.length() == CPF_LENGTH) {
                hasCpf = true;
            }
        }

        if (hasCpf) {
            return ITIBrasilCertType.ECPF;
        }

        throw noIdentifierFound();
    }

    private static boolean isOtherName(List<?> entry) {
        return entry.size() >= 2
                && entry.get(0) instanceof Integer
                && (Integer) entry.get(0) == SAN_TYPE_OTHER_NAME
                && entry.get(1) instanceof byte[];
    }

    /**
     * Lê o DER de um OtherName: SEQUENCE { type-id OID, value [0] EXPLICIT OCTET STRING }.
     */
    private static OtherName parseOtherName(byte[] der) {
        ASN1Sequence sequence = ASN1Sequence.getInstance(der);
        String oid = ASN1ObjectIdentifier.getInstance(sequence.getObjectAt(0)).getId();
        ASN1TaggedObject tagged = ASN1TaggedObject.getInstance(sequence.getObjectAt(1));
        byte[] octets = ASN1OctetString.getInstance(tagged.getBaseObject()).getOctets();

        return new OtherName(oid, new String(octets, StandardCharsets.US_ASCII).trim());
    }

    private static IllegalArgumentException noIdentifierFound() {
        return new IllegalArgumentException("Não foi possível resgatar os valores de CPF ou CNPJ do certificado informado.");
    }

    private record OtherName(String oid, String value) {
    }
}
