package com.souhenriza.autenticacao;

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
public final class JanelaDeManutencao {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalTime INICIO = LocalTime.of(1, 0);
    private static final LocalTime FIM = LocalTime.of(3, 0);

    private JanelaDeManutencao() {
    }

    /**
     * Verifica se o instante cai dentro da janela de parada.
     *
     * @param instante instante a verificar, não nulo
     * @return tempo restante até o fim da janela, se o instante estiver dentro dela; vazio caso contrário
     *         (tipo genérico {@code Optional<Duration>})
     * @throws NullPointerException se {@code instante} for nulo
     */
    public static Optional<Duration> tempoRestante(Instant instante) {
        ZonedDateTime local = instante.atZone(FUSO);
        LocalTime hora = local.toLocalTime();

        if (hora.isBefore(INICIO) || !hora.isBefore(FIM)) {
            return Optional.empty();
        }

        return Optional.of(Duration.between(instante, local.with(FIM).toInstant()));
    }
}
