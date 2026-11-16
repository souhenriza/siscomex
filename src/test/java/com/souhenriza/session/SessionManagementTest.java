package com.souhenriza.session;

import com.souhenriza.auth.Authenticator;
import com.souhenriza.error.SiscomexException;
import com.souhenriza.support.MutableClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionManagementTest {

    private static final Instant START = Instant.parse("2026-11-15T15:00:00Z");
    private static final Duration DUE = Duration.ofMinutes(55);
    private static final Duration MIN_INTERVAL = Duration.ofSeconds(60);

    private final MutableClock clock = new MutableClock(START);
    private final AtomicInteger authentications = new AtomicInteger();
    private final Deque<Tokens> nextTokens = new ArrayDeque<>();
    private final AtomicReference<Duration> totalPaused = new AtomicReference<>(Duration.ZERO);

    private final Authenticator authenticator = () -> {
        authentications.incrementAndGet();
        return nextTokens.isEmpty() ? new Tokens("jwt-" + authentications.get(), "csrf-" + authentications.get())
                : nextTokens.poll();
    };

    private SessionManagement sessionManagement() {
        return new SessionManagement(authenticator, clock, DUE, MIN_INTERVAL, duration -> {
            totalPaused.updateAndGet(total -> total.plus(duration));
            clock.advance(duration);
        });
    }

    @DisplayName("Reutiliza a mesma sessão enquanto ela está válida, sem nova autenticação")
    @Test
    void reusesTheSameSessionWhileItIsValid() {
        SessionManagement sessions = sessionManagement();

        String first = sessions.getValidSession().authToken();
        String second = sessions.getValidSession().authToken();

        assertEquals(first, second);
        assertEquals(1, authentications.get());
    }

    @DisplayName("Autentica novamente quando o prazo de validade de 55 min é atingido")
    @Test
    void authenticatesAgainWhenTheDueTimeIsReached() {
        SessionManagement sessions = sessionManagement();
        String first = sessions.getValidSession().authToken();

        clock.advance(DUE);
        String second = sessions.getValidSession().authToken();

        assertNotEquals(first, second);
        assertEquals(2, authentications.get());
    }

    @DisplayName("Sessão invalidada é substituída no próximo uso")
    @Test
    void invalidatedSessionIsReplacedOnNextUse() {
        SessionManagement sessions = sessionManagement();
        var used = sessions.getValidSession();

        sessions.invalidate(used);
        clock.advance(MIN_INTERVAL);
        var replacement = sessions.getValidSession();

        assertNotEquals(used.authToken(), replacement.authToken());
        assertEquals(2, authentications.get());
    }

    @DisplayName("Aguarda o intervalo mínimo de 60 s entre autenticações")
    @Test
    void waitsMinimumIntervalBetweenAuthentications() {
        SessionManagement sessions = sessionManagement();
        var used = sessions.getValidSession();

        sessions.invalidate(used);
        sessions.getValidSession();

        assertEquals(MIN_INTERVAL, totalPaused.get());
        assertEquals(2, authentications.get());
    }

    @DisplayName("execute() devolve a resposta sem repetir quando a sessão é aceita")
    @Test
    void executeReturnsResponseWithoutRetryWhenSessionIsAccepted() {
        SessionManagement sessions = sessionManagement();

        String response = sessions.execute(session -> "ok-" + session.authToken(), r -> false);

        assertEquals("ok-jwt-1", response);
        assertEquals(1, authentications.get());
    }

    @DisplayName("execute() repete uma vez com sessão nova quando a sessão é rejeitada")
    @Test
    void executeRetriesOnceWithFreshSessionWhenRejected() {
        SessionManagement sessions = sessionManagement();
        AtomicInteger calls = new AtomicInteger();

        String response = sessions.execute(
                session -> calls.incrementAndGet() == 1 ? "rejected" : "accepted-" + session.authToken(),
                "rejected"::equals);

        assertEquals(2, calls.get());
        assertEquals("accepted-jwt-2", response);
        assertEquals(2, authentications.get());
    }

    @DisplayName("execute() não entra em loop: se a repetição também for rejeitada, devolve a resposta")
    @Test
    void executeDoesNotLoopWhenRetryIsRejectedAgain() {
        SessionManagement sessions = sessionManagement();
        AtomicInteger calls = new AtomicInteger();

        String response = sessions.execute(session -> {
            calls.incrementAndGet();
            return "rejected";
        }, "rejected"::equals);

        assertEquals(2, calls.get());
        assertEquals("rejected", response);
    }

    @DisplayName("Renovação só do CSRF é armazenada e mantém o JWT e a data de login")
    @Test
    void csrfRenewalAloneIsStoredWithoutChangingAuthToken() {
        nextTokens.add(new Tokens("jwt-1", "csrf-1"));
        SessionManagement sessions = sessionManagement();
        var initial = sessions.getValidSession();

        sessions.updateFromResponse(null, "csrf-2");
        var renewed = sessions.getValidSession();

        assertEquals("jwt-1", renewed.authToken());
        assertEquals("csrf-2", renewed.csrfToken());
        assertEquals(initial.getAt(), renewed.getAt());
        assertEquals(1, authentications.get());
    }

    @DisplayName("Novo JWT recebido reinicia a janela de validade")
    @Test
    void newAuthTokenResetsTheValidityWindow() {
        SessionManagement sessions = sessionManagement();
        sessions.getValidSession();

        clock.advance(Duration.ofMinutes(50));
        sessions.updateFromResponse("jwt-novo", null);
        clock.advance(Duration.ofMinutes(50));
        var session = sessions.getValidSession();

        assertEquals("jwt-novo", session.authToken());
        assertEquals(1, authentications.get());
    }

    @DisplayName("Atualização sem sessão existente é ignorada")
    @Test
    void updateWithoutSessionIsIgnored() {
        SessionManagement sessions = sessionManagement();

        sessions.updateFromResponse("jwt", "csrf");

        assertEquals(0, authentications.get());
        sessions.getValidSession();
        assertEquals(1, authentications.get());
    }

    @DisplayName("Autenticador que devolve null gera SiscomexException")
    @Test
    void nullTokensFromAuthenticatorAreRejected() {
        SessionManagement sessions = new SessionManagement(() -> null, clock, DUE, MIN_INTERVAL, d -> clock.advance(d));

        assertThrows(SiscomexException.class, sessions::getValidSession);
    }

    @DisplayName("Falha de autenticação não é armazenada e a próxima chamada tenta de novo")
    @Test
    void failedAuthenticationIsNotStoredAndIsTriedAgain() {
        AtomicInteger attempts = new AtomicInteger();
        Authenticator flaky = () -> {
            if (attempts.incrementAndGet() == 1) {
                throw new SiscomexException("indisponível", new RuntimeException());
            }
            return new Tokens("jwt-ok", "csrf-ok");
        };
        SessionManagement sessions = new SessionManagement(flaky, clock, DUE, MIN_INTERVAL, d -> clock.advance(d));

        assertThrows(SiscomexException.class, sessions::getValidSession);
        assertEquals("jwt-ok", sessions.getValidSession().authToken());
        assertEquals(2, attempts.get());
    }

    @DisplayName("Prazo de validade zero ou negativo é recusado na construção")
    @Test
    void nonPositiveDueIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new SessionManagement(authenticator, clock, Duration.ZERO, MIN_INTERVAL, d -> { }));
    }
}
