package com.souhenriza.autenticacao;

import com.souhenriza.autenticacao.api.ClienteApi;
import com.souhenriza.autenticacao.erro.ExcecaoSiscomex;
import com.souhenriza.autenticacao.sessao.GerenciadorSessao;
import com.souhenriza.support.MutableClock;
import com.souhenriza.support.StubCredenciais;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlPortalTest {

    private static final Instant NOON = Instant.parse("2026-11-15T15:00:00Z");

    @Test
    @DisplayName("Aceita HTTPS e remove a barra final")
    void acceptsHttpsAndStripsTrailingSlash() {
        assertEquals("https://val.portalunico.siscomex.gov.br", UrlPortal.exigirHttps("https://val.portalunico.siscomex.gov.br/"));
        assertEquals("https://localhost:8443", UrlPortal.exigirHttps("  https://localhost:8443  "));
    }

    @Test
    @DisplayName("Esquema HTTPS é reconhecido sem diferenciar maiúsculas")
    void schemeIsCaseInsensitive() {
        assertEquals("HTTPS://localhost", UrlPortal.exigirHttps("HTTPS://localhost"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:8080",
            "http://val.portalunico.siscomex.gov.br/",
            "ftp://portal.example.gov.br",
            "portal.siscomex.gov.br",
            "https:/portal.siscomex.gov.br",
            "https://",
            // o texto "https" não basta: o esquema precisa ser HTTPS
            "http://evil.example/https",
            "http://https.example.com",
    })
    @DisplayName("Recusa URLs que não usam HTTPS ou não têm host")
    void rejectsNonHttpsUrls(String url) {
        ExcecaoSiscomex e = assertThrows(ExcecaoSiscomex.class, () -> UrlPortal.exigirHttps(url));
        assertTrue(e.getMessage().contains("HTTPS") || e.getMessage().contains("inválida")
                || e.getMessage().contains("host"), e.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Recusa URL nula, vazia ou só com espaços")
    void rejectsBlankUrls(String url) {
        if (url == null) {
            assertThrows(NullPointerException.class, () -> UrlPortal.exigirHttps(null));
        } else {
            assertThrows(ExcecaoSiscomex.class, () -> UrlPortal.exigirHttps(url));
        }
    }

    @Test
    @DisplayName("ClienteApi não aceita URL base em HTTP")
    void apiClientRejectsHttp() {
        var clock = new MutableClock(NOON);
        var sessions = new GerenciadorSessao(() -> {
            throw new AssertionError("não deve autenticar");
        }, clock, Duration.ofMinutes(55), Duration.ZERO, d -> { });

        assertThrows(ExcecaoSiscomex.class,
                () -> new ClienteApi("http://localhost:8080", sessions, HttpClient.newHttpClient(), clock));
    }

    @Test
    @DisplayName("AutenticadorHTTP não aceita URL base em HTTP")
    void authenticatorRejectsHttp() {
        assertThrows(ExcecaoSiscomex.class, () -> new AutenticadorHTTP("http://localhost:8080",
                new StubCredenciais(PerfilAcesso.AGECARGA), HttpClient.newHttpClient(), Duration.ofSeconds(5),
                new MutableClock(NOON)));
    }
}
