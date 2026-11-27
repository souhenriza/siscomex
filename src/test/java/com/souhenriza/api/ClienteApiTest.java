package com.souhenriza.api;

import com.souhenriza.autenticacao.AutenticadorHTTP;
import com.souhenriza.autenticacao.PerfilAcesso;
import com.souhenriza.autenticacao.api.ClienteApi;
import com.souhenriza.autenticacao.api.RequisicaoSiscomex;
import com.souhenriza.autenticacao.api.RespostaSiscomex;
import com.souhenriza.autenticacao.erro.CodigoErroPortal;
import com.souhenriza.autenticacao.erro.ExcecaoSiscomex;
import com.souhenriza.autenticacao.sessao.Pausa;
import com.souhenriza.autenticacao.sessao.GerenciadorSessao;
import com.souhenriza.support.LocalPortal;
import com.souhenriza.support.LocalPortal.Reply;
import com.souhenriza.support.MutableClock;
import com.souhenriza.support.StubCredenciais;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClienteApiTest {

    private static final String AUTH_PATH = "/portal/api/autenticar";
    private static final String DUIMP_PATH = "/duimp-api/api/ext/duimp/21BR00000012345/1";

    /** 12:00 em Brasília, fora da janela de parada. */
    private static final Instant NOON = Instant.parse("2026-11-15T15:00:00Z");
    /** 02:30 em Brasília, dentro da janela de parada. */
    private static final Instant HALF_PAST_TWO = Instant.parse("2026-11-15T05:30:00Z");

    private LocalPortal portal;

    @BeforeEach
    void startPortal() throws IOException {
        portal = new LocalPortal();
    }

    @AfterEach
    void stopPortal() {
        portal.close();
    }

    private ClienteApi client(Instant now) {
        Clock clock = new MutableClock(now);
        HttpClient http = portal.httpClient();
        AutenticadorHTTP authenticator = new AutenticadorHTTP(portal.url(),
                new StubCredenciais(PerfilAcesso.AGECARGA), http, Duration.ofSeconds(5), clock);
        // Intervalo mínimo zero: os testes não devem esperar o limite de 60 s do portal.
        GerenciadorSessao sessions = new GerenciadorSessao(authenticator, clock,
                Duration.ofMinutes(55), Duration.ZERO, Pausa.bloqueante());
        return new ClienteApi(portal.url(), sessions, http, clock);
    }

    private List<LocalPortal.Request> requestsTo(String path) {
        return portal.requests().stream().filter(r -> r.path().equals(path)).toList();
    }

    @Test
    @DisplayName("Envia Authorization com o JWT e X-CSRF-Token com o CSRF da sessão autenticada")
    void sendsJwtAndCsrfFromAuthenticatedSession() {
        portal.replyForPath(AUTH_PATH, new Reply(200, "", "jwt-1", "csrf-1"));
        portal.replyForPath(DUIMP_PATH, new Reply(200, "{}", null, null));

        client(NOON).send(RequisicaoSiscomex.get(DUIMP_PATH));

        LocalPortal.Request sent = requestsTo(DUIMP_PATH).get(0);
        assertEquals("GET", sent.method());
        assertEquals("jwt-1", sent.header("Authorization"));
        assertEquals("csrf-1", sent.header("X-CSRF-Token"));
    }

    @Test
    @DisplayName("Envia corpo JSON com Content-Type application/json quando a requisição tem corpo")
    void sendsJsonBodyWithContentType() {
        portal.replyForPath(AUTH_PATH, new Reply(200, "", "jwt-1", "csrf-1"));
        portal.replyForPath(DUIMP_PATH, new Reply(200, "{}", null, null));

        client(NOON).send(RequisicaoSiscomex.post(DUIMP_PATH, "{\"numero\":\"21BR00000012345\"}"));

        LocalPortal.Request sent = requestsTo(DUIMP_PATH).get(0);
        assertEquals("POST", sent.method());
        assertEquals("application/json", sent.header("Content-Type"));
    }

    @Test
    @DisplayName("Usa o CSRF renovado devolvido na resposta anterior, sem nova autenticação")
    void usesCsrfRenewedByPreviousResponse() {
        portal.replyForPath(AUTH_PATH, new Reply(200, "", "jwt-1", "csrf-1"));
        portal.replyForPath(DUIMP_PATH,
                new Reply(200, "{}", null, "csrf-renovado"),
                new Reply(200, "{}", null, null));
        ClienteApi api = client(NOON);

        api.send(RequisicaoSiscomex.get(DUIMP_PATH));
        api.send(RequisicaoSiscomex.get(DUIMP_PATH));

        List<LocalPortal.Request> sent = requestsTo(DUIMP_PATH);
        assertEquals("csrf-1", sent.get(0).header("X-CSRF-Token"));
        assertEquals("csrf-renovado", sent.get(1).header("X-CSRF-Token"));
        assertEquals(1, requestsTo(AUTH_PATH).size(), "a renovação do CSRF não deve autenticar de novo");
    }

    @Test
    @DisplayName("Em 401 autentica de novo e repete a chamada uma vez com o novo JWT")
    void reauthenticatesOnceAndRepeatsOnUnauthorized() {
        portal.replyForPath(AUTH_PATH,
                new Reply(200, "", "jwt-1", "csrf-1"),
                new Reply(200, "", "jwt-2", "csrf-2"));
        portal.replyForPath(DUIMP_PATH,
                new Reply(401, "", null, null),
                new Reply(200, "{\"numero\":\"21BR00000012345\"}", null, null));

        RespostaSiscomex response = client(NOON).send(RequisicaoSiscomex.get(DUIMP_PATH));

        assertEquals(200, response.status());
        assertEquals("{\"numero\":\"21BR00000012345\"}", response.corpo());
        assertEquals(2, requestsTo(AUTH_PATH).size());
        List<LocalPortal.Request> sent = requestsTo(DUIMP_PATH);
        assertEquals(2, sent.size());
        assertEquals("jwt-1", sent.get(0).header("Authorization"));
        assertEquals("jwt-2", sent.get(1).header("Authorization"));
        assertEquals("csrf-2", sent.get(1).header("X-CSRF-Token"));
    }

    @Test
    @DisplayName("Se o portal continuar respondendo 401 após nova autenticação, lança ExcecaoSiscomex")
    void throwsWhenSessionStillRejectedAfterReauthentication() {
        portal.replyForPath(AUTH_PATH,
                new Reply(200, "", "jwt-1", "csrf-1"),
                new Reply(200, "", "jwt-2", "csrf-2"));
        portal.replyForPath(DUIMP_PATH, new Reply(401, "", null, null));
        ClienteApi api = client(NOON);

        ExcecaoSiscomex e = assertThrows(ExcecaoSiscomex.class,
                () -> api.send(RequisicaoSiscomex.get(DUIMP_PATH)));

        assertTrue(e.getMessage().contains("401"), e.getMessage());
        assertEquals(2, requestsTo(DUIMP_PATH).size(), "a chamada deve ser repetida apenas uma vez");
    }

    @Test
    @DisplayName("Respostas de erro de negócio (422) são devolvidas ao chamador com o corpo original")
    void returnsBusinessErrorsToCaller() {
        String body = "{\"internal-code\":\"DIMP-ER0001\",\"message\":\"Erro de validação\"}";
        portal.replyForPath(AUTH_PATH, new Reply(200, "", "jwt-1", "csrf-1"));
        portal.replyForPath(DUIMP_PATH, new Reply(422, body, null, null));

        RespostaSiscomex response = client(NOON).send(RequisicaoSiscomex.get(DUIMP_PATH));

        assertEquals(422, response.status());
        assertFalse(response.ehSucesso());
        assertEquals(body, response.corpo());
        assertEquals(1, requestsTo(DUIMP_PATH).size(), "erro de negócio não deve repetir a chamada");
    }

    @Test
    @DisplayName("Durante a janela de parada recusa a chamada sem contactar o portal")
    void refusesCallsDuringMaintenanceWindowWithoutContactingPortal() {
        ClienteApi api = client(HALF_PAST_TWO);

        ExcecaoSiscomex e = assertThrows(ExcecaoSiscomex.class,
                () -> api.send(RequisicaoSiscomex.get(DUIMP_PATH)));

        assertEquals(CodigoErroPortal.PARADA_PROGRAMADA, e.codigoErroPortal().orElseThrow());
        assertTrue(e.tempoEspera().isPresent(), "a exceção deve informar quanto tempo falta para a janela terminar");
        assertTrue(portal.requests().isEmpty(), "nenhuma requisição deve chegar ao portal");
    }
}
