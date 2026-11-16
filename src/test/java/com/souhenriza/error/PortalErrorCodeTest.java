package com.souhenriza.error;

import com.souhenriza.auth.error.PortalErrorCode;
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

class PortalErrorCodeTest {

    @DisplayName("Os códigos do enum são exatamente os 14 códigos documentados, na mesma ordem")
    @Test
    void codesMatchTheDocumentedAuthenticationErrors() {
        List<String> documented = List.of(
                "PUCX-ER0101", "PUCX-ER0102", "PUCX-ER0401",
                "PLAT-ER2001", "PLAT-ER2008", "PLAT-ER2013", "PLAT-ER2023", "PLAT-ER2033", "PLAT-ER3005",
                "PLAT-ER8001", "PLAT-ER8002", "PLAT-ER8003", "PLAT-ER8004", "PLAT-ER8008");

        List<String> mapped = Arrays.stream(PortalErrorCode.values()).map(PortalErrorCode::code).toList();

        assertEquals(documented, mapped);
    }

    @DisplayName("Cada código do enum é encontrado por fromCode a partir do próprio valor")
    @ParameterizedTest
    @EnumSource(PortalErrorCode.class)
    void everyCodeIsFoundByItsOwnValue(PortalErrorCode code) {
        assertEquals(Optional.of(code), PortalErrorCode.fromCode(code.code()));
    }

    @DisplayName("Código desconhecido ou nulo não é mapeado (Optional vazio)")
    @Test
    void unknownAndMissingCodesAreNotMapped() {
        assertEquals(Optional.empty(), PortalErrorCode.fromCode("XYZ-9999"));
        assertEquals(Optional.empty(), PortalErrorCode.fromCode(null));
    }

    @DisplayName("Limite de taxa (PLAT-ER2033) e manutenção (PUCX-ER0401) são marcados como retentáveis")
    @Test
    void rateLimitAndMaintenanceAreRetryable() {
        assertTrue(PortalErrorCode.AUTH_RATE_LIMIT.retryable());
        assertTrue(PortalErrorCode.MAINTENANCE_WINDOW.retryable());
    }

    @DisplayName("Erros de configuração, perfil e certificado não são retentáveis")
    @ParameterizedTest
    @EnumSource(value = PortalErrorCode.class, names = {"MTLS_HANDSHAKE_FAILED", "ECNPJ_NOT_ALLOWED_FOR_PROFILE",
            "NO_PROFILE_REPRESENTATION", "CNPJ_NOT_ENABLED_FOR_PROFILE", "IRREGULAR_CPF", "INVALID_ROLE_TYPE"})
    void configurationErrorsAreNotRetryable(PortalErrorCode code) {
        assertFalse(code.retryable(), code.code());
    }
}
