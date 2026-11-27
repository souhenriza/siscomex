package com.souhenriza.duimp.model.importador.modelos;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ImportadorCover(
        @JsonProperty("tipoImportador")
        TipoImportador tipoImportador,

        String ni
) {
}
