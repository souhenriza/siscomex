package com.souhenriza;

import com.souhenriza.autenticacao.Autenticador;
import com.souhenriza.autenticacao.AutenticadorHTTP;
import com.souhenriza.autenticacao.PerfilAcesso;
import com.souhenriza.autenticacao.sessao.Tokens;
import com.souhenriza.support.LocalPortal;
import com.souhenriza.support.MutableClock;
import com.souhenriza.support.StubCredenciais;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SiscomexTest {

    private final StubCredenciais credentials = new StubCredenciais(PerfilAcesso.IMPEXP);
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
    void environmentUrlsMatchTheDocumentation(Ambiente ambiente, String url) {
        assertEquals(url, ambiente.getUrl());
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
                () -> new Siscomex.Builder().environment(Ambiente.VALIDACAO).build());
    }

    @DisplayName("Instância construída mantém ambiente e credenciais e expõe o GerenciadorSessao")
    @Test
    void builtInstanceKeepsEnvironmentAndCredentials() {
        Siscomex siscomex = new Siscomex.Builder()
                .environment(Ambiente.VALIDACAO)
                .credentials(credentials)
                .build();

        assertEquals(Ambiente.VALIDACAO, siscomex.getEnvironment());
        assertSame(credentials, siscomex.getCredential());
        assertNotNull(siscomex.getSessionManagement());
    }

    @DisplayName("A sessão é obtida pelo autenticador injetado")
    @Test
    void sessionIsObtainedThroughTheInjectedAuthenticator() {
        Autenticador fake = () -> new Tokens("jwt-fake", "csrf-fake");
        Siscomex siscomex = new Siscomex(Ambiente.PRODUCAO, credentials, fake);

        assertEquals("jwt-fake", siscomex.getSessionManagement().obterSessaoValida().tokenAutenticacao());
    }

    /** Fluxo completo: Siscomex -> GerenciadorSessao -> AutenticadorHTTP -> portal (servidor local). */
    @DisplayName("Fluxo completo: o Siscomex obtém a sessão do portal e envia o Role-Type configurado")
    @Test
    void sessionIsAuthenticatedAgainstThePortalWithTheConfiguredProfile() {
        portal.replyWithTokens("jwt-do-portal", "csrf-do-portal");
        var authenticator = new AutenticadorHTTP(portal.url(), credentials, portal.httpClient(),
                Duration.ofSeconds(5), new MutableClock(Instant.parse("2026-11-15T15:00:00Z")));
        Siscomex siscomex = new Siscomex(Ambiente.VALIDACAO, credentials, authenticator);

        var session = siscomex.getSessionManagement().obterSessaoValida();

        assertEquals("jwt-do-portal", session.tokenAutenticacao());
        assertEquals("csrf-do-portal", session.tokenCsrf());
        assertEquals("IMPEXP", portal.requests().get(0).header("Role-Type"));
        assertEquals(1, portal.requests().size());
    }
}
