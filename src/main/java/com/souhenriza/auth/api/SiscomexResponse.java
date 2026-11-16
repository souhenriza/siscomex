package com.souhenriza.auth.api;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Resposta de um serviço do portal. Os nomes dos headers são comparados sem diferenciar maiúsculas.
 *
 * @param status  código HTTP
 * @param body    corpo da resposta, como texto
 * @param headers primeiro valor de cada header da resposta
 */
public record SiscomexResponse(int status, String body, Map<String, String> headers) {

    private static final Set<String> SENSITIVE_HEADERS = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    static {
        SENSITIVE_HEADERS.addAll(List.of("Set-Token", "X-CSRF-Token", "Authorization", "Cookie", "Set-Cookie"));
    }

    public SiscomexResponse {
        Map<String, String> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (headers != null) {
            copy.putAll(headers);
        }
        headers = Collections.unmodifiableMap(copy);
    }

    /** Indica se o status está na faixa de sucesso (2xx). */
    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }

    public Optional<String> header(String name) {
        return Optional.ofNullable(headers.get(name));
    }

    /**
     * Não inclui os valores de headers de sessão (JWT e CSRF), para que um log da resposta não vaze tokens.
     */
    @Override
    public String toString() {
        Map<String, String> masked = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.forEach((name, value) -> masked.put(name, SENSITIVE_HEADERS.contains(name) ? "***" : value));
        return "SiscomexResponse[status=" + status + ", body=" + body + ", headers=" + masked + "]";
    }
}
