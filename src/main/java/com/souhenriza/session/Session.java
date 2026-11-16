package com.souhenriza.session;

import java.time.Instant;
import java.util.Objects;

public record Session(String authToken, String csrfToken, Instant getAt) {
    public Session{
        if (authToken == null || authToken.isBlank()) {
            throw new IllegalArgumentException("authToken é obrigatório");
        }

        Objects.requireNonNull(getAt, "obtidaEm");
    }

    @Override
    public String toString() {
        return "Session{" +
                "getAt=" + getAt +
                '}';
    }
}
