package com.souhenriza.autenticacao.sessao;

import java.time.Instant;
import java.util.Objects;

/**
 * Sessão ativa no portal: token de autenticação (JWT), token CSRF e o instante em que foi obtida.
 *
 * @param tokenAutenticacao token JWT enviado no header {@code Authorization}; não pode ser vazio
 * @param tokenCsrf         token enviado no header {@code X-CSRF-Token}; pode ser nulo
 * @param obtidaEm          instante em que a sessão foi obtida ou renovada; não pode ser nulo
 */
public record Sessao(String tokenAutenticacao, String tokenCsrf, Instant obtidaEm) {

    /**
     * Valida os campos da sessão.
     *
     * @throws IllegalArgumentException se {@code tokenAutenticacao} for nulo ou vazio
     * @throws NullPointerException     se {@code obtidaEm} for nulo
     */
    public Sessao {
        if (tokenAutenticacao == null || tokenAutenticacao.isBlank()) {
            throw new IllegalArgumentException("tokenAutenticacao é obrigatório");
        }

        Objects.requireNonNull(obtidaEm, "obtidaEm");
    }

    /**
     * Resumo seguro para log: mostra só o instante de obtenção, sem os tokens.
     *
     * @return texto no formato {@code Sessao{obtidaEm=...}} (tipo não genérico)
     */
    @Override
    public String toString() {
        return "Sessao{" +
                "obtidaEm=" + obtidaEm +
                '}';
    }
}
