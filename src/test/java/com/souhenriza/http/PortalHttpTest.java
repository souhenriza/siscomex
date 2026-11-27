package com.souhenriza.http;

import com.souhenriza.autenticacao.erro.ExcecaoSiscomex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testes unitários de {@link PortalHttp} que não abrem conexão: validação do construtor e montagem do
 * {@link HttpRequest}. O envio real é coberto por {@link PortalHttpIntegrationTest}.
 */
class PortalHttpTest {

    private static final String BASE = "https://localhost:8443";
    private static final String PATH = "/duimp-api/api/ext/duimp/21BR00000012345/1";
    private static final Duration TIMEOUT = Duration.ofSeconds(7);

    private final PortalHttp portal = new PortalHttp(BASE, HttpClient.newHttpClient(), TIMEOUT);

    @Test
    @DisplayName("Construtor aceita base URL HTTPS e ignora a barra final")
    void acceptsHttpsBaseUrlAndTrimsTrailingSlash() {
        PortalHttp comBarra = new PortalHttp(BASE + "/", HttpClient.newHttpClient(), TIMEOUT);

        HttpRequest request = comBarra.toHttpRequest(PortalRequest.get(PATH));

        assertEquals(URI.create(BASE + PATH), request.uri());
    }

    @Test
    @DisplayName("Construtor recusa base URL que não usa HTTPS")
    void rejectsNonHttpsBaseUrl() {
        assertThrows(ExcecaoSiscomex.class,
                () -> new PortalHttp("http://localhost:8080", HttpClient.newHttpClient(), TIMEOUT));
    }

    @Test
    @DisplayName("Construtor recusa argumentos nulos")
    void rejectsNullArguments() {
        HttpClient client = HttpClient.newHttpClient();
        assertThrows(NullPointerException.class, () -> new PortalHttp(null, client, TIMEOUT));
        assertThrows(NullPointerException.class, () -> new PortalHttp(BASE, null, TIMEOUT));
        assertThrows(NullPointerException.class, () -> new PortalHttp(BASE, client, null));
    }

    @Test
    @DisplayName("Construtor recusa timeout zero ou negativo")
    void rejectsNonPositiveTimeout() {
        HttpClient client = HttpClient.newHttpClient();
        assertThrows(IllegalArgumentException.class, () -> new PortalHttp(BASE, client, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new PortalHttp(BASE, client, Duration.ofSeconds(-1)));
    }

    @Test
    @DisplayName("GET sem corpo: método, URL com query, Accept JSON, sem Content-Type e timeout da instância")
    void getHasDefaultsWithoutContentType() {
        PortalRequest request = PortalRequest.get(PATH).param("limit", 10);

        HttpRequest built = portal.toHttpRequest(request);

        assertEquals("GET", built.method());
        assertEquals(URI.create(BASE + PATH + "?limit=10"), built.uri());
        assertEquals("application/json", built.headers().firstValue("Accept").orElseThrow());
        assertFalse(built.headers().firstValue("Content-Type").isPresent());
        assertEquals(TIMEOUT, built.timeout().orElseThrow());
        assertEquals(0L, built.bodyPublisher().orElseThrow().contentLength());
    }

    @Test
    @DisplayName("POST com corpo: Content-Type JSON e Content-Length igual ao tamanho do corpo em UTF-8")
    void postWithBodyHasJsonContentType() {
        String json = "{\"nome\":\"João\"}";

        HttpRequest built = portal.toHttpRequest(PortalRequest.post(PATH).json(json));

        assertEquals("POST", built.method());
        assertEquals("application/json", built.headers().firstValue("Content-Type").orElseThrow());
        assertEquals(json.getBytes(StandardCharsets.UTF_8).length,
                built.bodyPublisher().orElseThrow().contentLength());
    }

    @Test
    @DisplayName("PUT e DELETE usam o método correspondente")
    void putAndDeleteUseTheirMethods() {
        assertEquals("PUT", portal.toHttpRequest(PortalRequest.put(PATH).json("{}")).method());
        assertEquals("DELETE", portal.toHttpRequest(PortalRequest.delete(PATH)).method());
    }

    @Test
    @DisplayName("Header do chamador substitui o padrão Accept, sem duplicar o valor")
    void callerHeaderOverridesDefault() {
        HttpRequest built = portal.toHttpRequest(PortalRequest.get(PATH).header("accept", "text/plain"));

        assertEquals(List.of("text/plain"), built.headers().allValues("Accept"));
    }

    @Test
    @DisplayName("Headers de sessão do chamador chegam à requisição")
    void sessionHeadersAreSent() {
        HttpRequest built = portal.toHttpRequest(PortalRequest.get(PATH)
                .header("Authorization", "jwt-1")
                .header("X-CSRF-Token", "csrf-1"));

        assertEquals("jwt-1", built.headers().firstValue("Authorization").orElseThrow());
        assertEquals("csrf-1", built.headers().firstValue("X-CSRF-Token").orElseThrow());
    }

    @Test
    @DisplayName("toHttpRequest recusa requisição nula")
    void toHttpRequestRejectsNull() {
        assertThrows(NullPointerException.class, () -> portal.toHttpRequest(null));
    }
}
