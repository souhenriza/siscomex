package com.souhenriza.duimp;

/**
 * Um erro de negócio devolvido pelo portal, do bloco {@code errors} da resposta.
 *
 * @param code    código do erro, por exemplo {@code DIMP-ER9999}
 * @param field   campo a que o erro se refere, quando informado
 * @param message descrição do erro
 */
public record ErroNegocio(String code, String field, String message) {
}
