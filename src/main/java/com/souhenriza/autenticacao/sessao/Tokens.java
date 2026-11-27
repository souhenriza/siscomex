package com.souhenriza.autenticacao.sessao;

/**
 * Par de tokens devolvido pela autenticação: o JWT e o token CSRF.
 *
 * @param tokenAutenticacao token JWT; não pode ser vazio
 * @param tokenCsrf         token CSRF; pode ser nulo
 */
public record Tokens(String tokenAutenticacao, String tokenCsrf) {

    /**
     * Valida o token de autenticação.
     *
     * @throws IllegalArgumentException se {@code tokenAutenticacao} for nulo ou vazio
     */
    public Tokens {
        if (tokenAutenticacao == null || tokenAutenticacao.isBlank()) {
            throw new IllegalArgumentException("tokenAutenticacao é obrigatório");
        }
    }

    /**
     * Resumo seguro para log: os valores dos tokens são mascarados.
     *
     * @return texto com os tokens substituídos por {@code ***} (tipo não genérico)
     */
    @Override
    public String toString() {
        return "Tokens{tokenAutenticacao=***, tokenCsrf=***}";
    }
}
