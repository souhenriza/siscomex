package com.souhenriza.support;

import com.souhenriza.autenticacao.PerfilAcesso;
import com.souhenriza.autenticacao.credencial.Credenciais;

import javax.net.ssl.SSLContext;
import java.util.Map;

/** Credenciais sem TLS, para testar o fluxo HTTP contra o servidor local. */
public record StubCredenciais(PerfilAcesso perfil, Map<String, String> cabecalhos) implements Credenciais {

    public StubCredenciais(PerfilAcesso perfil) {
        this(perfil, Map.of());
    }

    @Override
    public SSLContext contextoSsl() {
        return null;
    }

    @Override
    public Map<String, String> cabecalhosAutenticacao() {
        return cabecalhos;
    }
}
