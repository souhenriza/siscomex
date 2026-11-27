package com.souhenriza.autenticacao.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Corpo de erro devolvido pelo portal na autenticação. Os nomes em inglês dos campos JSON são o contrato
 * com o portal e são preservados por {@link JsonProperty}; os componentes têm nomes em português.
 *
 * @param status   código HTTP informado no corpo
 * @param mensagem texto de erro do portal (campo JSON {@code message})
 * @param tag      etiqueta do erro
 * @param codigo   código do erro, por exemplo {@code PUCX-ER0101} (campo JSON {@code code})
 */
public record ErroRespostaAutenticacao(
        int status,
        @JsonProperty("message") String mensagem,
        String tag,
        @JsonProperty("code") String codigo
) {
}
