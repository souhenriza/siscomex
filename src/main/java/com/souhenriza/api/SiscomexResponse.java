package com.souhenriza.api;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Resposta de um serviço do portal. Os nomes dos headers são comparados sem diferenciar maiúsculas.
 *
 * @param status  código HTTP
 * @param body    corpo da resposta, como texto
 * @param headers primeiro valor de cada header da resposta
 */
public record SiscomexResponse(int status, String body, Map<String, String> headers) {

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
}
