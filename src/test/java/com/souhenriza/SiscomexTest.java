package com.souhenriza;

import com.souhenriza.auth.Authenticator;
import com.souhenriza.auth.AuthenticatorHTTP;
import com.souhenriza.auth.RoleType;
import com.souhenriza.auth.session.Tokens;
import com.souhenriza.support.LocalPortal;
import com.souhenriza.support.MutableClock;
import com.souhenriza.support.StubCredentials;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SiscomexTest {

    private final StubCredentials credentials = new StubCredentials(RoleType.IMPEXP);
    private LocalPortal portal;

    @BeforeEach
    void startPortal() throws IOException {
        portal = new LocalPortal();
    }

    @AfterEach
    void stopPortal() {
        portal.close();
    }

    @DisplayName("URLs de PRODUCAO, VALIDACAO e HOMOLOGACAO conferem com a documentação de ambientes")
    @ParameterizedTest
    @CsvSource({
            "PRODUCAO,    https://portalunico.siscomex.gov.br/",
            "VALIDACAO,   https://val.portalunico.siscomex.gov.br/",
            "HOMOLOGACAO, https://hom.pucomex.serpro.gov.br/"
    })
    void environmentUrlsMatchTheDocumentation(Environment environment, String url) {
        assertEquals(url, environment.getUrl());
    }

    @DisplayName("Builder sem ambiente lança NullPointerException")
    @Test
    void builderRequiresEnvironment() {
        assertThrows(NullPointerException.class, () -> new Siscomex.Builder().credentials(credentials).build());
    }

    @DisplayName("Builder sem credenciais lança NullPointerException")
    @Test
    void builderRequiresCredentials() {
        assertThrows(NullPointerException.class,
                () -> new Siscomex.Builder().environment(Environment.VALIDACAO).build());
    }

    @DisplayName("Instância construída mantém ambiente e credenciais e expõe o SessionManagement")
    @Test
    void builtInstanceKeepsEnvironmentAndCredentials() {
        Siscomex siscomex = new Siscomex.Builder()
                .environment(Environment.VALIDACAO)
                .credentials(credentials)
                .build();

        assertEquals(Environment.VALIDACAO, siscomex.getAmbiente());
        assertSame(credentials, siscomex.getCredential());
        assertNotNull(siscomex.getSessionManagement());
    }

    @DisplayName("A sessão é obtida pelo autenticador injetado")
    @Test
    void sessionIsObtainedThroughTheInjectedAuthenticator() {
        Authenticator fake = () -> new Tokens("jwt-fake", "csrf-fake");
        Siscomex siscomex = new Siscomex(Environment.PRODUCAO, credentials, fake);

        assertEquals("jwt-fake", siscomex.getSessionManagement().getValidSession().authToken());
    }

    /** Fluxo completo: Siscomex -> SessionManagement -> AuthenticatorHTTP -> portal (servidor local). */
    @DisplayName("Fluxo completo: o Siscomex obtém a sessão do portal e envia o Role-Type configurado")
    @Test
    void sessionIsAuthenticatedAgainstThePortalWithTheConfiguredProfile() {
        portal.replyWithTokens("jwt-do-portal", "csrf-do-portal");
        var authenticator = new AuthenticatorHTTP(portal.url(), credentials, portal.httpClient(),
                Duration.ofSeconds(5), new MutableClock(Instant.parse("2026-11-15T15:00:00Z")));
        Siscomex siscomex = new Siscomex(Environment.VALIDACAO, credentials, authenticator);

        var session = siscomex.getSessionManagement().getValidSession();

        assertEquals("jwt-do-portal", session.authToken());
        assertEquals("csrf-do-portal", session.csrfToken());
        assertEquals("IMPEXP", portal.requests().get(0).header("Role-Type"));
        assertEquals(1, portal.requests().size());
    }
}
