package com.souhenriza.duimp.model.importador.modelos;

import com.fasterxml.jackson.annotation.JsonProperty;

public record IdentificacaoCapaCover(
        @JsonProperty(value = "importador")
        ImportadorCover importadorCover,

        String informacaoComplementar
) {
}
