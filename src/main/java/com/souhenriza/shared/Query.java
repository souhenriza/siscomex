package com.souhenriza.shared;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Montagem de query string. Parâmetros com valor {@code null} são omitidos, e os demais são codificados.
 * <pre>{@code
 * Query.novo().param("offset", 0).param("ordenacao", null).aplicar("/base") // "/base?offset=0"
 * }</pre>
 */
public final class Query {

    private final StringBuilder parametros = new StringBuilder();

    private Query() {
    }

    public static Query novo() {
        return new Query();
    }

    /** Acrescenta um parâmetro. Valor nulo não gera nada; o mesmo nome pode ser repetido. */
    public Query param(String nome, Object valor) {
        if (valor == null) {
            return this;
        }
        parametros.append(parametros.isEmpty() ? '?' : '&')
                .append(nome).append('=').append(codificar(String.valueOf(valor)));
        return this;
    }

    /** Anexa os parâmetros acumulados ao caminho informado. */
    public String aplicar(String caminho) {
        return caminho + parametros;
    }

    public static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
