package com.souhenriza.auth.session;

import com.souhenriza.auth.Authenticator;
import com.souhenriza.auth.error.SiscomexException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Predicate;

public class SessionManagement {


    // Obtém o token de authenticação antes mesmo do obrigatório determinado pelo contrato
    private static final Duration DUE_PATTERN = Duration.ofMinutes(55);
    private static final Duration MIN_INTERVAL = Duration.ofSeconds(60);

    private final Authenticator authenticator;
    private final Clock clock;
    private final Duration due;
    private final Pause pause;
    private final Duration minInterval;

    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicReference<Session> actual = new AtomicReference<>();

    private Instant lastTry;

    public SessionManagement(Authenticator authenticator) {
        this(authenticator, Clock.systemUTC(), DUE_PATTERN, MIN_INTERVAL, Pause.thread());
    }

    public SessionManagement(Authenticator authenticator, Clock clock, Duration due, Duration minInterval,
                             Pause pause) {
        this.authenticator = Objects.requireNonNull(authenticator, "autenticador");
        this.clock = Objects.requireNonNull(clock, "relogio");
        this.due = Objects.requireNonNull(due, "validade");
        this.pause = Objects.requireNonNull(pause);
        this.minInterval = Objects.requireNonNull(minInterval, "intervaloMinimo");
        if (due.isNegative() || due.isZero()) {
            throw new IllegalArgumentException("validade deve ser positiva");
        }

        if (minInterval.isNegative()) {
            throw new IllegalArgumentException("intervaloMinimo não pode ser negativo");
        }
    }

    public Session getValidSession() {
        Session s = actual.get();

        if (valid(s)) {
            return s;
        }

        lock.lock();;

        try {
            s = actual.get();
            if (valid(s)) {
                return s;
            }
            return authenticate();
        } finally {
            lock.unlock();
        }
    }

    public void updateFromResponse(String newAuthToken, String newCSRFToken) {
        boolean hasAuth = newAuthToken != null && !newAuthToken.isBlank();
        boolean hasCSRF = newCSRFToken != null && !newCSRFToken.isBlank();

        if (!hasAuth && !hasCSRF) {
            return;
        }

        Instant now = clock.instant();

        actual.updateAndGet(s -> {
            if (s == null) {
                return  null;
            }

            return new Session(hasAuth ? newAuthToken : s.authToken(),
                    hasCSRF ? newCSRFToken : s.csrfToken(),
                    hasAuth ? now : s.getAt());
        });
    }

    public void invalidate(Session usedSession) {
        Objects.requireNonNull(usedSession, "sessaoUsada");
        actual.updateAndGet(s -> (s != null && s.authToken().equals(usedSession.authToken())) ? null : s);
    }

    public <R> R execute(Function<Session, R> call, Predicate<R> rejectedSession) {
        Objects.requireNonNull(call, "chamada");
        Objects.requireNonNull(rejectedSession, "sessaoRejeitada");

        Session used = getValidSession();
        R response = call.apply(used);
        if (!rejectedSession.test(response)) {
            return response;
        }

        invalidate(used);
        return call.apply(getValidSession());
    }
    private Session authenticate() {
        waitMinInterval();
        lastTry = clock.instant();
        Tokens tokens = authenticator.authenticate();

        if (tokens == null) {
            throw new SiscomexException("O autenticador não retornou tokens.");
        }
        Session newSession = new Session(tokens.authToken(), tokens.csrfToken(), clock.instant());
        actual.set(newSession);
        return newSession;
    }

    private void waitMinInterval() {
        Duration lack = pendingRelease();
        while (!lack.isNegative() && !lack.isZero()) {
            try {
                pause.sleep(lack);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SiscomexException("Interrompido enquanto aguardva o intervalo mínimo entre as autenticações", e);
            }
            lack = pendingRelease();
        }
    }

    private Duration pendingRelease() {
        if (lastTry == null) {
            return Duration.ZERO;
        }

        Duration elapsed = Duration.between(lastTry, clock.instant());

        return minInterval.minus(elapsed);
    }

    private boolean valid(Session s) {
        return s != null && clock.instant().isBefore(s.getAt().plus(due));
    }

}
