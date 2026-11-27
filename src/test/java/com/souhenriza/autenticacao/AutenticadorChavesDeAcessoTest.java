package com.souhenriza.autenticacao;

import com.souhenriza.autenticacao.credencial.ChavesDeAcesso;
import com.souhenriza.autenticacao.erro.CodigoErroPortal;
import com.souhenriza.autenticacao.erro.ExcecaoSiscomex;
import com.souhenriza.autenticacao.sessao.Tokens;
import com.souhenriza.support.LocalPortal;
import com.souhenriza.support.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Autenticação por chave de acesso contra o servidor local: confere o endpoint, os headers enviados e a leitura
 * dos tokens. Sem certificado de cliente.
 */
class AutenticadorChavesDeAcessoTest {

    private static final String CAMINHO_USUARIO = "/portal/api/autenticar/chave-acesso";

    /** 12:00 em Brasília, fora da janela de parada. */
    private static final Instant NOON = Instant.parse("2026-11-15T15:00:00Z");

    private LocalPortal portal;

    @BeforeEach
    void startPortal() throws IOException {
        portal = new LocalPortal();
    }

    @AfterEach
    void stopPortal() {
        portal.close();
    }

    private AutenticadorHTTP autenticador(ChavesDeAcesso chaves) {
        return new AutenticadorHTTP(portal.url(), chaves, portal.httpClient(), Duration.ofSeconds(5),
                new MutableClock(NOON));
    }

    @Test
    @DisplayName("Chave de usuário envia Client-Id, Client-Secret e Role-Type para /chave-acesso e recebe os tokens")
    void userKeySendsHeadersToUserEndpoint() {
        portal.replyWithTokens("jwt-chave", "csrf-chave");
        ChavesDeAcesso chaves = ChavesDeAcesso.deChaveDeAcesso("id-usuario", "segredo", PerfilAcesso.AGECARGA);

        Tokens tokens = autenticador(chaves).autenticar();

        assertEquals("jwt-chave", tokens.tokenAutenticacao());
        assertEquals("csrf-chave", tokens.tokenCsrf());
        LocalPortal.Request enviada = portal.requests().get(0);
        assertEquals("POST", enviada.method());
        assertEquals(CAMINHO_USUARIO, enviada.path());
        assertEquals("id-usuario", enviada.header("Client-Id"));
        assertEquals("segredo", enviada.header("Client-Secret"));
        assertEquals("AGECARGA", enviada.header("Role-Type"));
    }

    @Test
    @DisplayName("Chave recusada pelo portal (PLAT-ER8008) lança exceção com o código mapeado e sem repetir")
    void rejectedKeyReportsMappedCode() {
        portal.replyWithError(401, "{\"status\":401,\"message\":\"Chave inválida\",\"tag\":\"t\",\"code\":\"PLAT-ER8008\"}");
        AutenticadorHTTP autenticador = autenticador(ChavesDeAcesso.deChaveDeAcesso("id", "errado", PerfilAcesso.AGECARGA));

        ExcecaoSiscomex e = assertThrows(ExcecaoSiscomex.class, autenticador::autenticar);

        assertEquals(CodigoErroPortal.AUTENTICACAO_CHAVE_ACESSO_FALHOU, e.codigoErroPortal().orElseThrow());
        assertFalse(e.ehRepetivel());
        assertEquals(1, portal.requests().size());
    }
}
