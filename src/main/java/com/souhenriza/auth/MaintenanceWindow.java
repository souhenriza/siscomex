package com.souhenriza.auth;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Janela de parada programada do Portal Único (diariamente, entre 01:00 e 03:00).
 * A documentação não informa o fuso; assume-se o horário de Brasília (America/Sao_Paulo).
 */
public final class MaintenanceWindow {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalTime START = LocalTime.of(1, 0);
    private static final LocalTime END = LocalTime.of(3, 0);

    private MaintenanceWindow() {
    }

    /**
     * Verifica se o instante cai dentro da janela de parada.
     *
     * @return o tempo restante até o fim da janela, se o instante estiver dentro dela; vazio caso contrário.
     */
    public static Optional<Duration> remainingAt(Instant instant) {
        ZonedDateTime local = instant.atZone(ZONE);
        LocalTime time = local.toLocalTime();

        if (time.isBefore(START) || !time.isBefore(END)) {
            return Optional.empty();
        }

        return Optional.of(Duration.between(instant, local.with(END).toInstant()));
    }
}
