package com.souhenriza.auth;

import com.souhenriza.auth.api.ApiClient;
import com.souhenriza.auth.error.SiscomexException;
import com.souhenriza.auth.session.SessionManagement;
import com.souhenriza.support.MutableClock;
import com.souhenriza.support.StubCredentials;
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

class PortalUrlTest {

    private static final Instant NOON = Instant.parse("2026-11-15T15:00:00Z");

    @Test
    @DisplayName("Aceita HTTPS e remove a barra final")
    void acceptsHttpsAndStripsTrailingSlash() {
        assertEquals("https://val.portalunico.siscomex.gov.br", PortalUrl.requireHttps("https://val.portalunico.siscomex.gov.br/"));
        assertEquals("https://localhost:8443", PortalUrl.requireHttps("  https://localhost:8443  "));
    }

    @Test
    @DisplayName("Esquema HTTPS é reconhecido sem diferenciar maiúsculas")
    void schemeIsCaseInsensitive() {
        assertEquals("HTTPS://localhost", PortalUrl.requireHttps("HTTPS://localhost"));
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
        SiscomexException e = assertThrows(SiscomexException.class, () -> PortalUrl.requireHttps(url));
        assertTrue(e.getMessage().contains("HTTPS") || e.getMessage().contains("inválida")
                || e.getMessage().contains("host"), e.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Recusa URL nula, vazia ou só com espaços")
    void rejectsBlankUrls(String url) {
        if (url == null) {
            assertThrows(NullPointerException.class, () -> PortalUrl.requireHttps(null));
        } else {
            assertThrows(SiscomexException.class, () -> PortalUrl.requireHttps(url));
        }
    }

    @Test
    @DisplayName("ApiClient não aceita URL base em HTTP")
    void apiClientRejectsHttp() {
        var clock = new MutableClock(NOON);
        var sessions = new SessionManagement(() -> {
            throw new AssertionError("não deve autenticar");
        }, clock, Duration.ofMinutes(55), Duration.ZERO, d -> { });

        assertThrows(SiscomexException.class,
                () -> new ApiClient("http://localhost:8080", sessions, HttpClient.newHttpClient(), clock));
    }

    @Test
    @DisplayName("AuthenticatorHTTP não aceita URL base em HTTP")
    void authenticatorRejectsHttp() {
        assertThrows(SiscomexException.class, () -> new AuthenticatorHTTP("http://localhost:8080",
                new StubCredentials(RoleType.AGECARGA), HttpClient.newHttpClient(), Duration.ofSeconds(5),
                new MutableClock(NOON)));
    }
}
