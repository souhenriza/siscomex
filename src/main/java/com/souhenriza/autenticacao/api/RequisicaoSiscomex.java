package com.souhenriza.autenticacao.api;

import java.util.Objects;

/**
 * Requisição a um serviço do portal. O {@code caminho} é o caminho a partir do host, incluindo a base do serviço,
 * por exemplo {@code /duimp-api/api/ext/duimp/21BR00000012345/1}. Os headers de autenticação ({@code Authorization}
 * e {@code X-CSRF-Token}) são acrescentados pelo cliente.
 *
 * @param metodo  método HTTP ({@code GET}, {@code POST}, {@code PUT} ou {@code DELETE})
 * @param caminho caminho do serviço, começando com {@code /}
 * @param corpo   corpo JSON, ou {@code null} quando não há corpo
 */
public record RequisicaoSiscomex(String metodo, String caminho, String corpo) {

    /**
     * Valida os campos da requisição.
     *
     * @throws NullPointerException     se {@code metodo} ou {@code caminho} for nulo
     * @throws IllegalArgumentException se {@code caminho} não começar com {@code /}
     */
    public RequisicaoSiscomex {
        Objects.requireNonNull(metodo, "metodo");
        Objects.requireNonNull(caminho, "caminho");
        if (!caminho.startsWith("/")) {
            throw new IllegalArgumentException("O caminho do serviço deve começar com '/'.");
        }
    }

    /**
     * Requisição {@code GET} sem corpo.
     *
     * @param caminho caminho do serviço
     * @return requisição nova (tipo não genérico)
     * @throws NullPointerException     se {@code caminho} for nulo
     * @throws IllegalArgumentException se {@code caminho} não começar com {@code /}
     */
    public static RequisicaoSiscomex get(String caminho) {
        return new RequisicaoSiscomex("GET", caminho, null);
    }

    /**
     * Requisição {@code GET} com corpo JSON (incomum, mas aceito pelo portal em alguns serviços).
     *
     * @param caminho  caminho do serviço
     * @param jsonBody corpo JSON
     * @return requisição nova (tipo não genérico)
     * @throws NullPointerException     se {@code caminho} for nulo
     * @throws IllegalArgumentException se {@code caminho} não começar com {@code /}
     */
    public static RequisicaoSiscomex get(String caminho, String jsonBody) {
        return new RequisicaoSiscomex("GET", caminho, jsonBody);
    }

    /**
     * Requisição {@code POST} com corpo JSON.
     *
     * @param caminho  caminho do serviço
     * @param jsonBody corpo JSON, não nulo
     * @return requisição nova (tipo não genérico)
     * @throws NullPointerException     se {@code caminho} ou {@code jsonBody} for nulo
     * @throws IllegalArgumentException se {@code caminho} não começar com {@code /}
     */
    public static RequisicaoSiscomex post(String caminho, String jsonBody) {
        return new RequisicaoSiscomex("POST", caminho, Objects.requireNonNull(jsonBody, "corpo"));
    }

    /**
     * Requisição {@code PUT} com corpo JSON.
     *
     * @param caminho  caminho do serviço
     * @param jsonBody corpo JSON, não nulo
     * @return requisição nova (tipo não genérico)
     * @throws NullPointerException     se {@code caminho} ou {@code jsonBody} for nulo
     * @throws IllegalArgumentException se {@code caminho} não começar com {@code /}
     */
    public static RequisicaoSiscomex put(String caminho, String jsonBody) {
        return new RequisicaoSiscomex("PUT", caminho, Objects.requireNonNull(jsonBody, "corpo"));
    }

    /**
     * Requisição {@code DELETE} sem corpo.
     *
     * @param caminho caminho do serviço
     * @return requisição nova (tipo não genérico)
     * @throws NullPointerException     se {@code caminho} for nulo
     * @throws IllegalArgumentException se {@code caminho} não começar com {@code /}
     */
    public static RequisicaoSiscomex delete(String caminho) {
        return new RequisicaoSiscomex("DELETE", caminho, null);
    }
}
