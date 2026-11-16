package com.souhenriza.auth.session;

public record Tokens(String authToken, String csrfToken) {
    public Tokens{
        if (authToken == null || authToken.isBlank()) {
            throw new IllegalArgumentException("authToken é obrigatório");
        }
    }

    @Override
    public String toString() {
        return "Tokens{authToken=***, csrfToken=***}";
    }
}

