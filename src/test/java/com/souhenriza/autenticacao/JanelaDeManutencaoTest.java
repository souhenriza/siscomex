package com.souhenriza.autenticacao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JanelaDeManutencaoTest {

    @DisplayName("Janela de 01:00 às 03:00 (Brasília): limites, dentro e fora da janela, horário UTC convertido e tempo restante")
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "2026-11-15T00:59:59-03:00, ",
            "2026-11-15T01:00:00-03:00, PT2H",
            "2026-11-15T02:30:00-03:00, PT30M",
            "2026-11-15T02:59:59-03:00, PT1S",
            "2026-11-15T03:00:00-03:00, ",
            "2026-11-15T12:00:00-03:00, ",
            "2026-11-15T04:30:00Z, PT1H30M"
    })
    void windowIsFromOneToThreeInBrasilia(String instant, String expectedRemaining) {
        Optional<Duration> remaining = JanelaDeManutencao.tempoRestante(parse(instant));

        assertEquals(expectedRemaining == null ? Optional.empty() : Optional.of(Duration.parse(expectedRemaining)),
                remaining);
    }

    private static Instant parse(String value) {
        return OffsetDateTime.parse(value).toInstant();
    }
}
