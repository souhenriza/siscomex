package com.souhenriza.autenticacao.certificado;

import com.souhenriza.support.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdentificadorCertificadoTest {

    @DisplayName("Identifica como e-CPF o certificado com CPF nos dados do titular (2.16.76.1.3.1, posições 9 a 19)")
    @Test
    void identifiesECpfFromHolderData() throws Exception {
        assertEquals(TipoCertificadoICPBrasil.ECPF, IdentificadorCertificado.identificar(Fixtures.certificate("ecpf.p12")));
    }

    @DisplayName("Aceita os dados do titular codificados como PrintableString, além de OCTET STRING")
    @Test
    void identifiesECpfEncodedAsPrintableString() throws Exception {
        assertEquals(TipoCertificadoICPBrasil.ECPF, IdentificadorCertificado.identificar(Fixtures.certificate("ecpf_printable_string.p12")));
    }

    @DisplayName("Identifica como e-CNPJ um certificado com o OID de CNPJ (2.16.76.1.3.3)")
    @Test
    void identifiesECnpjFromCnpjOid() throws Exception {
        assertEquals(TipoCertificadoICPBrasil.ECNPJ, IdentificadorCertificado.identificar(Fixtures.certificate("ecnpj.p12")));
    }

    @DisplayName("Identifica como e-CNPJ um certificado com CNPJ alfanumérico")
    @Test
    void identifiesAlphanumericCnpj() throws Exception {
        assertEquals(TipoCertificadoICPBrasil.ECNPJ, IdentificadorCertificado.identificar(Fixtures.certificate("ecnpj_alfanumerico.p12")));
    }

    /** e-CNPJ também carrega os dados do responsável; a ordem das entradas SAN não pode mudar o resultado. */
    @DisplayName("Mantém e-CNPJ quando os dados do responsável aparecem antes do CNPJ na SAN")
    @Test
    void eCnpjWithResponsibleDataListedFirstIsStillECnpj() throws Exception {
        assertEquals(TipoCertificadoICPBrasil.ECNPJ,
                IdentificadorCertificado.identificar(Fixtures.certificate("ecnpj_responsible_cpf_first.p12")));
    }

    /**
     * Sem CPF do titular nem CNPJ: CPF curto ou zerado, apenas o responsável (2.16.76.1.3.4, que não torna o
     * certificado um e-CPF), o layout antigo e incorreto (11 dígitos em 2.16.76.1.3.4), apenas nome ou sem SAN.
     */
    @DisplayName("Rejeita certificados sem CPF do titular ou CNPJ válido")
    @ParameterizedTest
    @ValueSource(strings = {"ecpf_short_cpf.p12", "ecpf_zero_cpf.p12", "responsible_only.p12",
            "ecpf_legacy_layout.p12", "name_only.p12", "no_san.p12"})
    void rejectsCertificatesWithoutValidCpfOrCnpj(String fixture) throws Exception {
        var certificate = Fixtures.certificate(fixture);
        assertThrows(IllegalArgumentException.class, () -> IdentificadorCertificado.identificar(certificate));
    }
}
