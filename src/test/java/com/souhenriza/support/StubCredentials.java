package com.souhenriza.support;

import com.souhenriza.auth.RoleType;
import com.souhenriza.auth.credential.Credentials;

import javax.net.ssl.SSLContext;
import java.util.Map;

/** Credenciais sem TLS, para testar o fluxo HTTP contra o servidor local. */
public record StubCredentials(RoleType roleType, Map<String, String> headers) implements Credentials {

    public StubCredentials(RoleType roleType) {
        this(roleType, Map.of());
    }

    @Override
    public SSLContext sslContext() {
        return null;
    }

    @Override
    public Map<String, String> authHeaders() {
        return headers;
    }
}
