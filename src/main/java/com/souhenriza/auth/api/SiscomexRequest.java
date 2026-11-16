package com.souhenriza.auth.api;

import java.util.Objects;

/**
 * Requisição a um serviço do portal. O {@code path} é o caminho a partir do host, incluindo a Base URL
 * do serviço, por exemplo {@code /duimp-api/api/ext/duimp/21BR00000012345/1}. Os headers de autenticação
 * ({@code Authorization} e {@code X-CSRF-Token}) são acrescentados pelo cliente.
 *
 * @param method método HTTP ({@code GET}, {@code POST}, {@code PUT}, {@code DELETE})
 * @param path   caminho do serviço, começando com {@code /}
 * @param body   corpo JSON, ou {@code null} quando não há corpo
 */
public record SiscomexRequest(String method, String path, String body) {

    public SiscomexRequest {
        Objects.requireNonNull(method, "metodo");
        Objects.requireNonNull(path, "caminho");
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("O caminho do serviço deve começar com '/'.");
        }
    }

    public static SiscomexRequest get(String path) {
        return new SiscomexRequest("GET", path, null);
    }

    public static SiscomexRequest post(String path, String jsonBody) {
        return new SiscomexRequest("POST", path, Objects.requireNonNull(jsonBody, "corpo"));
    }

    public static SiscomexRequest put(String path, String jsonBody) {
        return new SiscomexRequest("PUT", path, Objects.requireNonNull(jsonBody, "corpo"));
    }

    public static SiscomexRequest delete(String path) {
        return new SiscomexRequest("DELETE", path, null);
    }
}
