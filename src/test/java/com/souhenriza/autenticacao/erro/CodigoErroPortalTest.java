package com.souhenriza.autenticacao.erro;

import com.souhenriza.autenticacao.erro.CodigoErroPortal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodigoErroPortalTest {

    @DisplayName("Os códigos do enum são exatamente os 14 códigos documentados, na mesma ordem")
    @Test
    void codesMatchTheDocumentedAuthenticationErrors() {
        List<String> documented = List.of(
                "PUCX-ER0101", "PUCX-ER0102", "PUCX-ER0401",
                "PLAT-ER2001", "PLAT-ER2008", "PLAT-ER2013", "PLAT-ER2023", "PLAT-ER2033", "PLAT-ER3005",
                "PLAT-ER8001", "PLAT-ER8002", "PLAT-ER8003", "PLAT-ER8008");

        List<String> mapped = Arrays.stream(CodigoErroPortal.values()).map(CodigoErroPortal::codigo).toList();

        assertEquals(documented, mapped);
    }

    @DisplayName("Cada código do enum é encontrado por deCodigo a partir do próprio valor")
    @ParameterizedTest
    @EnumSource(CodigoErroPortal.class)
    void everyCodeIsFoundByItsOwnValue(CodigoErroPortal code) {
        assertEquals(Optional.of(code), CodigoErroPortal.deCodigo(code.codigo()));
    }

    @DisplayName("Código desconhecido ou nulo não é mapeado (Optional vazio)")
    @Test
    void unknownAndMissingCodesAreNotMapped() {
        assertEquals(Optional.empty(), CodigoErroPortal.deCodigo("XYZ-9999"));
        assertEquals(Optional.empty(), CodigoErroPortal.deCodigo(null));
    }

    @DisplayName("Limite de taxa (PLAT-ER2033) e manutenção (PUCX-ER0401) são marcados como retentáveis")
    @Test
    void rateLimitAndMaintenanceAreRetryable() {
        assertTrue(CodigoErroPortal.LIMITE_AUTENTICACAO.repetivel());
        assertTrue(CodigoErroPortal.PARADA_PROGRAMADA.repetivel());
    }

    @DisplayName("Erros de configuração, perfil e certificado não são retentáveis")
    @ParameterizedTest
    @EnumSource(value = CodigoErroPortal.class, names = {"HANDSHAKE_MTLS_FALHOU", "ECNPJ_NAO_PERMITIDO_PARA_PERFIL",
            "SEM_REPRESENTACAO_NO_PERFIL", "CNPJ_NAO_HABILITADO_PARA_PERFIL", "CPF_IRREGULAR", "CABECALHO_PERFIL_INVALIDO"})
    void configurationErrorsAreNotRetryable(CodigoErroPortal code) {
        assertFalse(code.repetivel(), code.codigo());
    }
}
