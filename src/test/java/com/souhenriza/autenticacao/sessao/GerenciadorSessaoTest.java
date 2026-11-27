package com.souhenriza.autenticacao.sessao;

import com.souhenriza.autenticacao.Autenticador;
import com.souhenriza.autenticacao.erro.ExcecaoSiscomex;
import com.souhenriza.autenticacao.sessao.GerenciadorSessao;
import com.souhenriza.autenticacao.sessao.Tokens;
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

class GerenciadorSessaoTest {

    private static final Instant START = Instant.parse("2026-11-15T15:00:00Z");
    private static final Duration DUE = Duration.ofMinutes(55);
    private static final Duration MIN_INTERVAL = Duration.ofSeconds(60);

    private final MutableClock clock = new MutableClock(START);
    private final AtomicInteger authentications = new AtomicInteger();
    private final Deque<Tokens> nextTokens = new ArrayDeque<>();
    private final AtomicReference<Duration> totalPaused = new AtomicReference<>(Duration.ZERO);

    private final Autenticador authenticator = () -> {
        authentications.incrementAndGet();
        return nextTokens.isEmpty() ? new Tokens("jwt-" + authentications.get(), "csrf-" + authentications.get())
                : nextTokens.poll();
    };

    private GerenciadorSessao sessionManagement() {
        return new GerenciadorSessao(authenticator, clock, DUE, MIN_INTERVAL, duration -> {
            totalPaused.updateAndGet(total -> total.plus(duration));
            clock.advance(duration);
        });
    }

    @DisplayName("Reutiliza a mesma sessão enquanto ela está válida, sem nova autenticação")
    @Test
    void reusesTheSameSessionWhileItIsValid() {
        GerenciadorSessao sessions = sessionManagement();

        String first = sessions.obterSessaoValida().tokenAutenticacao();
        String second = sessions.obterSessaoValida().tokenAutenticacao();

        assertEquals(first, second);
        assertEquals(1, authentications.get());
    }

    @DisplayName("Autentica novamente quando o prazo de validade de 55 min é atingido")
    @Test
    void authenticatesAgainWhenTheDueTimeIsReached() {
        GerenciadorSessao sessions = sessionManagement();
        String first = sessions.obterSessaoValida().tokenAutenticacao();

        clock.advance(DUE);
        String second = sessions.obterSessaoValida().tokenAutenticacao();

        assertNotEquals(first, second);
        assertEquals(2, authentications.get());
    }

    @DisplayName("Sessão invalidada é substituída no próximo uso")
    @Test
    void invalidatedSessionIsReplacedOnNextUse() {
        GerenciadorSessao sessions = sessionManagement();
        var used = sessions.obterSessaoValida();

        sessions.invalidar(used);
        clock.advance(MIN_INTERVAL);
        var replacement = sessions.obterSessaoValida();

        assertNotEquals(used.tokenAutenticacao(), replacement.tokenAutenticacao());
        assertEquals(2, authentications.get());
    }

    @DisplayName("Aguarda o intervalo mínimo de 60 s entre autenticações")
    @Test
    void waitsMinimumIntervalBetweenAuthentications() {
        GerenciadorSessao sessions = sessionManagement();
        var used = sessions.obterSessaoValida();

        sessions.invalidar(used);
        sessions.obterSessaoValida();

        assertEquals(MIN_INTERVAL, totalPaused.get());
        assertEquals(2, authentications.get());
    }

    @DisplayName("execute() devolve a resposta sem repetir quando a sessão é aceita")
    @Test
    void executeReturnsResponseWithoutRetryWhenSessionIsAccepted() {
        GerenciadorSessao sessions = sessionManagement();

        String response = sessions.executar(session -> "ok-" + session.tokenAutenticacao(), r -> false);

        assertEquals("ok-jwt-1", response);
        assertEquals(1, authentications.get());
    }

    @DisplayName("execute() repete uma vez com sessão nova quando a sessão é rejeitada")
    @Test
    void executeRetriesOnceWithFreshSessionWhenRejected() {
        GerenciadorSessao sessions = sessionManagement();
        AtomicInteger calls = new AtomicInteger();

        String response = sessions.executar(
                session -> calls.incrementAndGet() == 1 ? "rejected" : "accepted-" + session.tokenAutenticacao(),
                "rejected"::equals);

        assertEquals(2, calls.get());
        assertEquals("accepted-jwt-2", response);
        assertEquals(2, authentications.get());
    }

    @DisplayName("execute() não entra em loop: se a repetição também for rejeitada, devolve a resposta")
    @Test
    void executeDoesNotLoopWhenRetryIsRejectedAgain() {
        GerenciadorSessao sessions = sessionManagement();
        AtomicInteger calls = new AtomicInteger();

        String response = sessions.executar(session -> {
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
        GerenciadorSessao sessions = sessionManagement();
        var initial = sessions.obterSessaoValida();

        sessions.atualizarAPartirDaResposta(null, "csrf-2");
        var renewed = sessions.obterSessaoValida();

        assertEquals("jwt-1", renewed.tokenAutenticacao());
        assertEquals("csrf-2", renewed.tokenCsrf());
        assertEquals(initial.obtidaEm(), renewed.obtidaEm());
        assertEquals(1, authentications.get());
    }

    @DisplayName("Novo JWT recebido reinicia a janela de validade")
    @Test
    void newAuthTokenResetsTheValidityWindow() {
        GerenciadorSessao sessions = sessionManagement();
        sessions.obterSessaoValida();

        clock.advance(Duration.ofMinutes(50));
        sessions.atualizarAPartirDaResposta("jwt-novo", null);
        clock.advance(Duration.ofMinutes(50));
        var session = sessions.obterSessaoValida();

        assertEquals("jwt-novo", session.tokenAutenticacao());
        assertEquals(1, authentications.get());
    }

    @DisplayName("Atualização sem sessão existente é ignorada")
    @Test
    void updateWithoutSessionIsIgnored() {
        GerenciadorSessao sessions = sessionManagement();

        sessions.atualizarAPartirDaResposta("jwt", "csrf");

        assertEquals(0, authentications.get());
        sessions.obterSessaoValida();
        assertEquals(1, authentications.get());
    }

    @DisplayName("Autenticador que devolve null gera ExcecaoSiscomex")
    @Test
    void nullTokensFromAuthenticatorAreRejected() {
        GerenciadorSessao sessions = new GerenciadorSessao(() -> null, clock, DUE, MIN_INTERVAL, d -> clock.advance(d));

        assertThrows(ExcecaoSiscomex.class, sessions::obterSessaoValida);
    }

    @DisplayName("Falha de autenticação não é armazenada e a próxima chamada tenta de novo")
    @Test
    void failedAuthenticationIsNotStoredAndIsTriedAgain() {
        AtomicInteger attempts = new AtomicInteger();
        Autenticador flaky = () -> {
            if (attempts.incrementAndGet() == 1) {
                throw new ExcecaoSiscomex("indisponível", new RuntimeException());
            }
            return new Tokens("jwt-ok", "csrf-ok");
        };
        GerenciadorSessao sessions = new GerenciadorSessao(flaky, clock, DUE, MIN_INTERVAL, d -> clock.advance(d));

        assertThrows(ExcecaoSiscomex.class, sessions::obterSessaoValida);
        assertEquals("jwt-ok", sessions.obterSessaoValida().tokenAutenticacao());
        assertEquals(2, attempts.get());
    }

    @DisplayName("Prazo de validade zero ou negativo é recusado na construção")
    @Test
    void nonPositiveDueIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new GerenciadorSessao(authenticator, clock, Duration.ZERO, MIN_INTERVAL, d -> { }));
    }
}
