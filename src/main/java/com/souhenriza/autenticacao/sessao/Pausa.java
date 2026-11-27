package com.souhenriza.autenticacao.sessao;

import java.time.Duration;

/**
 * Espera usada pelo gerenciador de sessão para respeitar o intervalo mínimo entre autenticações. Substituível nos
 * testes, para não esperar o tempo real.
 */
@FunctionalInterface
public interface Pausa {

    /**
     * Aguarda pelo tempo informado.
     *
     * @param duracao tempo a aguardar, não negativo
     * @throws InterruptedException se a thread for interrompida durante a espera
     */
    void dormir(Duration duracao) throws InterruptedException;

    /**
     * Pausa real, bloqueando a thread atual com {@link Thread#sleep(long)}.
     *
     * @return pausa que bloqueia a thread (tipo não genérico)
     */
    static Pausa bloqueante() {
        return duracao -> {
            long ms = (duracao.toNanos() + 999_999) / 1_000_000;
            Thread.sleep(ms);
        };
    }
}
