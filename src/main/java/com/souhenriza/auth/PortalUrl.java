package com.souhenriza.auth;

import com.souhenriza.auth.error.SiscomexException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

/** Validação da URL base do portal. Somente HTTPS é aceito: tokens e requisições não podem trafegar em claro. */
public final class PortalUrl {

    private PortalUrl() {
    }

    /**
     * Confere que a URL usa o esquema HTTPS e tem host.
     *
     * @return a URL sem a barra final
     * @throws SiscomexException se a URL for nula, malformada, não usar HTTPS ou não tiver host
     */
    public static String requireHttps(String baseUrl) {
        Objects.requireNonNull(baseUrl, "baseUrl");
        String trimmed = baseUrl.strip();

        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new SiscomexException("A URL do portal é inválida: " + trimmed, e);
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new SiscomexException("A URL do portal deve usar HTTPS. Conexões sem criptografia não são aceitas: "
                    + trimmed);
        }

        if (uri.getHost() == null || uri.getHost().isEmpty()) {
            throw new SiscomexException("A URL do portal não informa o host: " + trimmed);
        }

        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
