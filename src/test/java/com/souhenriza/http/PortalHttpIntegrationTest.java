package com.souhenriza.http;

import com.souhenriza.autenticacao.erro.ExcecaoSiscomex;
import com.souhenriza.support.LocalPortal;
import com.souhenriza.support.LocalPortal.Reply;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes de {@link PortalHttp} contra um servidor HTTPS local. Cobrem o envio real: método, cabeçalhos,
 * corpo, leitura da resposta e falhas de comunicação.
 */
class PortalHttpIntegrationTest {

    private static final String PATH = "/duimp-api/api/ext/duimp/21BR00000012345/1";

    private LocalPortal server;
    private PortalHttp portal;

    @BeforeEach
    void startServer() throws IOException {
        server = new LocalPortal();
        portal = new PortalHttp(server.url(), server.httpClient(), Duration.ofSeconds(5));
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    @DisplayName("GET devolve status, corpo e headers da resposta")
    void getReturnsStatusBodyAndHeaders() {
        server.replyForPath(PATH, new Reply(200, "{\"numero\":1}", "jwt-novo", "csrf-novo"));

        PortalResponse response = portal.send(PortalRequest.get(PATH));

        assertEquals(200, response.status());
        assertEquals("{\"numero\":1}", response.body());
        assertTrue(response.isSuccess());
        assertEquals("jwt-novo", response.header("set-token").orElseThrow());
        assertEquals("csrf-novo", response.header("X-CSRF-Token").orElseThrow());
    }

    @Test
    @DisplayName("GET envia Accept JSON e não envia Content-Type")
    void getSendsAcceptAndNoContentType() {
        server.replyForPath(PATH, new Reply(200, "{}", null, null));

        portal.send(PortalRequest.get(PATH));

        LocalPortal.Request sent = server.requests().get(0);
        assertEquals("GET", sent.method());
        assertEquals("application/json", sent.header("Accept"));
        assertNull(sent.header("Content-Type"));
    }

    @Test
    @DisplayName("POST envia o corpo JSON com Content-Type application/json")
    void postSendsJsonBody() {
        server.replyForPath(PATH, new Reply(200, "{}", null, null));

        portal.send(PortalRequest.post(PATH).json("{\"numero\":\"21BR00000012345\"}"));

        LocalPortal.Request sent = server.requests().get(0);
        assertEquals("POST", sent.method());
        assertEquals("application/json", sent.header("Content-Type"));
        assertEquals("{\"numero\":\"21BR00000012345\"}", sent.body());
    }

    @Test
    @DisplayName("Headers de sessão definidos pelo chamador chegam ao portal")
    void sessionHeadersReachServer() {
        server.replyForPath(PATH, new Reply(200, "{}", null, null));

        portal.send(PortalRequest.get(PATH).header("Authorization", "jwt-1").header("X-CSRF-Token", "csrf-1"));

        LocalPortal.Request sent = server.requests().get(0);
        assertEquals("jwt-1", sent.header("Authorization"));
        assertEquals("csrf-1", sent.header("X-CSRF-Token"));
    }

    @Test
    @DisplayName("Status de erro (401, 422, 500) é devolvido como resposta, sem lançar exceção")
    void errorStatusIsReturnedNotThrown() {
        server.replyForPath(PATH, new Reply(422, "{\"errors\":[]}", null, null));
        server.replyForPath("/outro", new Reply(500, "", null, null));

        PortalResponse unprocessable = portal.send(PortalRequest.get(PATH));
        PortalResponse failed = portal.send(PortalRequest.get("/outro"));

        assertEquals(422, unprocessable.status());
        assertEquals("{\"errors\":[]}", unprocessable.body());
        assertFalse(unprocessable.isSuccess());
        assertEquals(500, failed.status());
    }

    @Test
    @DisplayName("Resposta sem corpo chega como texto vazio")
    void emptyBodyIsEmptyString() {
        server.replyForPath(PATH, new Reply(204, "", null, null));

        PortalResponse response = portal.send(PortalRequest.delete(PATH));

        assertEquals(204, response.status());
        assertEquals("", response.body());
    }

    @Test
    @DisplayName("Portal inacessível gera ExcecaoSiscomex retentável, com IOException como causa")
    void unreachablePortalIsRetryableFailure() throws IOException {
        int closedPort;
        try (ServerSocket free = new ServerSocket(0)) {
            closedPort = free.getLocalPort();
        }
        PortalHttp unreachable = new PortalHttp("https://localhost:" + closedPort,
                HttpClient.newHttpClient(), Duration.ofSeconds(5));

        ExcecaoSiscomex e = assertThrows(ExcecaoSiscomex.class,
                () -> unreachable.send(PortalRequest.get(PATH)));

        assertTrue(e.ehRepetivel());
        assertInstanceOf(IOException.class, e.getCause());
    }

    @Test
    @DisplayName("Interrupção durante a espera gera ExcecaoSiscomex e restaura o estado de interrupção")
    void interruptionIsReportedAndFlagRestored() {
        Thread.currentThread().interrupt();
        try {
            ExcecaoSiscomex e = assertThrows(ExcecaoSiscomex.class,
                    () -> portal.send(PortalRequest.get(PATH)));

            assertEquals("Interrompido durante a comunicação com o portal.", e.getMessage());
            assertTrue(Thread.currentThread().isInterrupted(), "o estado de interrupção deve ser restaurado");
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @DisplayName("Requisição nula é recusada antes de qualquer envio")
    void nullRequestIsRejectedBeforeSending() {
        assertThrows(NullPointerException.class, () -> portal.send(null));
        assertEquals(0, server.requests().size());
    }
}
