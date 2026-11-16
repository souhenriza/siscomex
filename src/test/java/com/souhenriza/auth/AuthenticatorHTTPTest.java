package com.souhenriza.auth;

import com.souhenriza.auth.error.PortalErrorCode;
import com.souhenriza.auth.error.SiscomexException;
import com.souhenriza.auth.session.Tokens;
import com.souhenriza.support.LocalPortal;
import com.souhenriza.support.MutableClock;
import com.souhenriza.support.StubCredentials;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticatorHTTPTest {

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

    private AuthenticatorHTTP authenticator(StubCredentials credentials, Instant now) {
        return new AuthenticatorHTTP(portal.url(), credentials, portal.httpClient(),
                Duration.ofSeconds(5), new MutableClock(now));
    }

    private AuthenticatorHTTP authenticator(Instant now) {
        return authenticator(new StubCredentials(RoleType.AGECARGA), now);
    }

    @DisplayName("Retorna JWT e CSRF lidos dos headers Set-Token e X-CSRF-Token")
    @Test
    void returnsTokensFromResponseHeaders() {
        portal.replyWithTokens("jwt-do-usuario", "csrf-inicial");

        Tokens tokens = authenticator(NOON).authenticate();

        assertEquals("jwt-do-usuario", tokens.authToken());
        assertEquals("csrf-inicial", tokens.csrfToken());
    }

    @DisplayName("Envia POST para /portal/api/autenticar com o header Role-Type do perfil")
    @Test
    void sendsPostToAuthenticationPathWithRoleType() {
        portal.replyWithTokens("jwt", "csrf");

        authenticator(new StubCredentials(RoleType.IMPEXP), NOON).authenticate();

        var request = portal.requests().get(0);
        assertEquals("POST", request.method());
        assertEquals("/portal/api/autenticar", request.path());
        assertEquals("IMPEXP", request.header("Role-Type"));
    }

    @DisplayName("Envia os headers de autenticação fornecidos pelas credenciais (base para chaves de acesso)")
    @Test
    void sendsHeadersProvidedByCredentials() {
        portal.replyWithTokens("jwt", "csrf");
        var credentials = new StubCredentials(RoleType.AGECARGA, Map.of("System-Code", "SISTEMA-TESTE"));

        authenticator(credentials, NOON).authenticate();

        assertEquals("SISTEMA-TESTE", portal.requests().get(0).header("System-Code"));
    }

    @DisplayName("Resposta 200 sem Set-Token é rejeitada como erro de protocolo, não retentável")
    @Test
    void successWithoutSetTokenIsRejected() {
        portal.replyWith(new LocalPortal.Reply(200, "", null, "csrf"));

        SiscomexException e = assertThrows(SiscomexException.class, () -> authenticator(NOON).authenticate());

        assertTrue(e.getMessage().contains("token JWT"), e.getMessage());
        assertFalse(e.isRetryable());
    }

    @DisplayName("Resposta 200 sem X-CSRF-Token é rejeitada")
    @Test
    void successWithoutCsrfTokenIsRejected() {
        portal.replyWith(new LocalPortal.Reply(200, "", "jwt", null));

        SiscomexException e = assertThrows(SiscomexException.class, () -> authenticator(NOON).authenticate());

        assertTrue(e.getMessage().contains("CSRF"), e.getMessage());
    }

    @DisplayName("PLAT-ER2033 mapeia o código, expõe retryAfter de 60 s e é retentável")
    @Test
    void rateLimitExposesRetryAfterOfSixtySeconds() {
        portal.replyWithError(400, "{\"status\":400,\"message\":\"Operação não autorizada\",\"code\":\"PLAT-ER2033\"}");

        SiscomexException e = assertThrows(SiscomexException.class, () -> authenticator(NOON).authenticate());

        assertEquals(Optional.of(PortalErrorCode.AUTH_RATE_LIMIT), e.portalErrorCode());
        assertEquals(Optional.of(Duration.ofSeconds(60)), e.retryAfter());
        assertTrue(e.isRetryable());
    }

    @DisplayName("PLAT-ER2013 mapeia o código, preserva a mensagem do portal e não é retentável")
    @Test
    void profileErrorKeepsPortalMessageAndIsNotRetryable() {
        portal.replyWithError(403, "{\"status\":403,\"message\":\"Usuário sem representações\",\"code\":\"PLAT-ER2013\"}");

        SiscomexException e = assertThrows(SiscomexException.class, () -> authenticator(NOON).authenticate());

        assertEquals(Optional.of(PortalErrorCode.NO_PROFILE_REPRESENTATION), e.portalErrorCode());
        assertTrue(e.getMessage().startsWith("[PLAT-ER2013]"), e.getMessage());
        assertTrue(e.getMessage().contains("Usuário sem representações"), e.getMessage());
        assertFalse(e.isRetryable());
        assertEquals(Optional.empty(), e.retryAfter());
    }

    @DisplayName("Código desconhecido do portal é mantido na exceção e não é retentável")
    @Test
    void unknownPortalCodeIsKeptRawAndNotRetryable() {
        portal.replyWithError(400, "{\"status\":400,\"message\":\"Código novo\",\"code\":\"XYZ-9999\"}");

        SiscomexException e = assertThrows(SiscomexException.class, () -> authenticator(NOON).authenticate());

        assertEquals(Optional.empty(), e.portalErrorCode());
        assertEquals("XYZ-9999", e.getResponseError().code());
        assertTrue(e.getMessage().contains("XYZ-9999"), e.getMessage());
        assertFalse(e.isRetryable());
    }

    @DisplayName("Erro 502 com corpo HTML (não JSON) é retentável e não tem código do portal")
    @Test
    void nonJsonGatewayErrorIsRetryableWithoutPortalCode() {
        portal.replyWithError(502, "<html>Bad gateway</html>");

        SiscomexException e = assertThrows(SiscomexException.class, () -> authenticator(NOON).authenticate());

        assertEquals(Optional.empty(), e.portalErrorCode());
        assertTrue(e.getMessage().contains("HTTP 502"), e.getMessage());
        assertTrue(e.isRetryable());
    }

    @DisplayName("Erro 503 sem corpo é retentável")
    @Test
    void emptyServerErrorIsRetryable() {
        portal.replyWithError(503, "");

        SiscomexException e = assertThrows(SiscomexException.class, () -> authenticator(NOON).authenticate());

        assertTrue(e.getMessage().contains("HTTP 503"), e.getMessage());
        assertTrue(e.isRetryable());
    }

    @DisplayName("PUCX-ER0401 vindo do portal fora da janela local é retentável, sem retryAfter")
    @Test
    void maintenanceReportedByPortalIsRetryableWithoutRetryAfterOutsideWindow() {
        portal.replyWithError(400, "{\"status\":400,\"message\":\"Parada\",\"code\":\"PUCX-ER0401\"}");

        SiscomexException e = assertThrows(SiscomexException.class, () -> authenticator(NOON).authenticate());

        assertEquals(Optional.of(PortalErrorCode.MAINTENANCE_WINDOW), e.portalErrorCode());
        assertTrue(e.isRetryable());
        assertEquals(Optional.empty(), e.retryAfter());
    }

    @DisplayName("Requisição às 02:30 é negada localmente, com retryAfter de 30 min, sem chamar o portal")
    @Test
    void requestInsideMaintenanceWindowIsDeniedWithoutCallingThePortal() {
        SiscomexException e = assertThrows(SiscomexException.class, () -> authenticator(HALF_PAST_TWO).authenticate());

        assertEquals(Optional.of(PortalErrorCode.MAINTENANCE_WINDOW), e.portalErrorCode());
        assertEquals(Optional.of(Duration.ofMinutes(30)), e.retryAfter());
        assertEquals(0, portal.requests().size());
    }

    @DisplayName("Requisição às 03:00 é enviada, pois a janela termina às 03:00")
    @Test
    void requestAtThreeAmIsSentBecauseTheWindowEndsAtThreeAm() {
        portal.replyWithTokens("jwt", "csrf");

        Tokens tokens = authenticator(Instant.parse("2026-11-15T06:00:00Z")).authenticate();

        assertEquals("jwt", tokens.authToken());
        assertEquals(1, portal.requests().size());
    }

    @DisplayName("Portal inacessível gera falha de rede retentável com IOException como causa")
    @Test
    void unreachablePortalIsRetryableNetworkFailure() throws IOException {
        int closedPort;
        try (ServerSocket free = new ServerSocket(0)) {
            closedPort = free.getLocalPort();
        }
        var authenticator = new AuthenticatorHTTP("https://localhost:" + closedPort, new StubCredentials(RoleType.AGECARGA),
                HttpClient.newHttpClient(), Duration.ofSeconds(5), new MutableClock(NOON));

        SiscomexException e = assertThrows(SiscomexException.class, authenticator::authenticate);

        assertTrue(e.isRetryable());
        assertInstanceOf(IOException.class, e.getCause());
    }

    @DisplayName("Falha no handshake TLS é reportada como erro de TLS, com SSLException na causa, e não é retentável")
    @Test
    void handshakeFailureIsReportedAsTlsAndNotRetryable() throws Exception {
        try (ServerSocket tls = new ServerSocket(0)) {
            Thread closer = new Thread(() -> {
                try {
                    tls.accept().close();
                } catch (IOException ignored) {
                    // o teste encerra o socket ao final
                }
            });
            closer.setDaemon(true);
            closer.start();

            var authenticator = new AuthenticatorHTTP("https://localhost:" + tls.getLocalPort(),
                    new StubCredentials(RoleType.AGECARGA), HttpClient.newHttpClient(), Duration.ofSeconds(5),
                    new MutableClock(NOON));

            SiscomexException e = assertThrows(SiscomexException.class, authenticator::authenticate);

            assertTrue(e.getMessage().startsWith("Falha TLS"), e.getMessage());
            assertTrue(causedBy(e, SSLException.class), "a causa deve ser uma SSLException");
            assertFalse(e.isRetryable());
        }
    }

    private static boolean causedBy(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }
}
