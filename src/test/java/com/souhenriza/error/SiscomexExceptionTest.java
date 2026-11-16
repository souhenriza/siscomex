package com.souhenriza.error;

import com.souhenriza.auth.error.PortalErrorCode;
import com.souhenriza.auth.error.SiscomexException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiscomexExceptionTest {

    @DisplayName("Exceção simples não é retentável e não tem código do portal nem retryAfter")
    @Test
    void plainExceptionIsNotRetryable() {
        SiscomexException e = new SiscomexException("falha");

        assertFalse(e.isRetryable());
        assertEquals(Optional.empty(), e.retryAfter());
        assertEquals(Optional.empty(), e.portalErrorCode());
    }

    @DisplayName("Informar retryAfter torna a exceção retentável e expõe o tempo de espera")
    @Test
    void retryAfterMakesTheExceptionRetryable() {
        SiscomexException e = new SiscomexException("aguarde", null, null, Duration.ofSeconds(5));

        assertTrue(e.isRetryable());
        assertEquals(Optional.of(Duration.ofSeconds(5)), e.retryAfter());
    }

    @DisplayName("Código retentável torna a exceção retentável mesmo sem retryAfter")
    @Test
    void retryableCodeMakesTheExceptionRetryableEvenWithoutRetryAfter() {
        SiscomexException e = new SiscomexException("limite", null, PortalErrorCode.AUTH_RATE_LIMIT, null);

        assertTrue(e.isRetryable());
        assertEquals(Optional.empty(), e.retryAfter());
        assertEquals(Optional.of(PortalErrorCode.AUTH_RATE_LIMIT), e.portalErrorCode());
    }

    @DisplayName("Falha de rede marcada como retentável preserva a causa original")
    @Test
    void networkFailureCanBeMarkedRetryableAndKeepsCause() {
        IOException cause = new IOException("rede");
        SiscomexException e = new SiscomexException("sem conexão", cause, true);

        assertTrue(e.isRetryable());
        assertEquals(cause, e.getCause());
    }
}
