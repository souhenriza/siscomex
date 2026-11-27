package com.souhenriza.duimp.model.importador;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Link de navegação devolvido pelo portal (campos {@code _rel}, {@code _href} e {@code _method}).
 *
 * @param rel    relação do link com o recurso
 * @param href   endereço do recurso relacionado
 * @param method método HTTP a usar no link
 */
public record LinkDuimp(
        @JsonProperty("_rel") String rel,
        @JsonProperty("_href") String href,
        @JsonProperty("_method") String method) {
}
