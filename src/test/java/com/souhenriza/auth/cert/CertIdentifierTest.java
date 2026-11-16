package com.souhenriza.auth.cert;

import com.souhenriza.support.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CertIdentifierTest {

    @DisplayName("Identifica como e-CPF um certificado com o OID de CPF (2.16.76.1.3.4)")
    @Test
    void identifiesECpfFromCpfOid() throws Exception {
        assertEquals(ITIBrasilCertType.ECPF, CertIdentifier.identify(Fixtures.certificate("ecpf.p12")));
    }

    @DisplayName("Identifica como e-CNPJ um certificado com o OID de CNPJ (2.16.76.1.3.3)")
    @Test
    void identifiesECnpjFromCnpjOid() throws Exception {
        assertEquals(ITIBrasilCertType.ECNPJ, CertIdentifier.identify(Fixtures.certificate("ecnpj.p12")));
    }

    /** e-CNPJ também carrega o CPF do responsável; a ordem das entradas SAN não pode mudar o resultado. */
    @DisplayName("Mantém e-CNPJ quando o CPF do responsável aparece antes do CNPJ na SAN")
    @Test
    void eCnpjWithResponsibleCpfListedFirstIsStillECnpj() throws Exception {
        assertEquals(ITIBrasilCertType.ECNPJ,
                CertIdentifier.identify(Fixtures.certificate("ecnpj_responsible_cpf_first.p12")));
    }

    @DisplayName("Rejeita certificados sem CPF ou CNPJ válido (CPF curto, apenas nome ou sem SAN)")
    @ParameterizedTest
    @ValueSource(strings = {"ecpf_short_cpf.p12", "name_only.p12", "no_san.p12"})
    void rejectsCertificatesWithoutValidCpfOrCnpj(String fixture) throws Exception {
        var certificate = Fixtures.certificate(fixture);
        assertThrows(IllegalArgumentException.class, () -> CertIdentifier.identify(certificate));
    }
}
