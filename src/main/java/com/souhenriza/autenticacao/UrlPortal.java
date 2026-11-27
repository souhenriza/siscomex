package com.souhenriza.autenticacao;

import com.souhenriza.autenticacao.erro.ExcecaoSiscomex;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

/**
 * Validação da URL base do portal. Somente HTTPS é aceito: tokens e requisições não podem trafegar em claro.
 */
public final class UrlPortal {

    private UrlPortal() {
    }

    /**
     * Confere que a URL usa o esquema HTTPS e tem host.
     *
     * @param urlBase URL base do portal
     * @return a URL sem espaços nas pontas e sem a barra final (tipo não genérico)
     * @throws NullPointerException if {@code urlBase} for nulo
     * @throws ExcecaoSiscomex      se a URL for malformada, não usar HTTPS ou não tiver host
     */
    public static String exigirHttps(String urlBase) {
        Objects.requireNonNull(urlBase, "urlBase");
        String aparada = urlBase.strip();

        URI uri;
        try {
            uri = new URI(aparada);
        } catch (URISyntaxException e) {
            throw new ExcecaoSiscomex("A URL do portal é inválida: " + aparada, e);
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new ExcecaoSiscomex("A URL do portal deve usar HTTPS. Conexões sem criptografia não são aceitas: "
                    + aparada);
        }

        if (uri.getHost() == null || uri.getHost().isEmpty()) {
            throw new ExcecaoSiscomex("A URL do portal não informa o host: " + aparada);
        }

        return aparada.endsWith("/") ? aparada.substring(0, aparada.length() - 1) : aparada;
    }
}
