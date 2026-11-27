package com.souhenriza.autenticacao.erro;

import com.souhenriza.autenticacao.erro.CodigoErroPortal;
import com.souhenriza.autenticacao.erro.ExcecaoSiscomex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcecaoSiscomexTest {

    @DisplayName("Exceção simples não é retentável e não tem código do portal nem retryAfter")
    @Test
    void plainExceptionIsNotRetryable() {
        ExcecaoSiscomex e = new ExcecaoSiscomex("falha");

        assertFalse(e.ehRepetivel());
        assertEquals(Optional.empty(), e.tempoEspera());
        assertEquals(Optional.empty(), e.codigoErroPortal());
    }

    @DisplayName("Informar retryAfter torna a exceção retentável e expõe o tempo de espera")
    @Test
    void retryAfterMakesTheExceptionRetryable() {
        ExcecaoSiscomex e = new ExcecaoSiscomex("aguarde", null, null, Duration.ofSeconds(5));

        assertTrue(e.ehRepetivel());
        assertEquals(Optional.of(Duration.ofSeconds(5)), e.tempoEspera());
    }

    @DisplayName("Código retentável torna a exceção retentável mesmo sem retryAfter")
    @Test
    void retryableCodeMakesTheExceptionRetryableEvenWithoutRetryAfter() {
        ExcecaoSiscomex e = new ExcecaoSiscomex("limite", null, CodigoErroPortal.LIMITE_AUTENTICACAO, null);

        assertTrue(e.ehRepetivel());
        assertEquals(Optional.empty(), e.tempoEspera());
        assertEquals(Optional.of(CodigoErroPortal.LIMITE_AUTENTICACAO), e.codigoErroPortal());
    }

    @DisplayName("Falha de rede marcada como retentável preserva a causa original")
    @Test
    void networkFailureCanBeMarkedRetryableAndKeepsCause() {
        IOException cause = new IOException("rede");
        ExcecaoSiscomex e = new ExcecaoSiscomex("sem conexão", cause, true);

        assertTrue(e.ehRepetivel());
        assertEquals(cause, e.getCause());
    }
}
