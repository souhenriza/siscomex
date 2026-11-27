package com.souhenriza.duimp.model.importador;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.souhenriza.duimp.model.importador.modelos.CargaCover;
import com.souhenriza.duimp.model.importador.modelos.DocumentosCover;
import com.souhenriza.duimp.model.importador.modelos.IdentificacaoCapaCover;

public record Duimp (
        @JsonProperty(value = "identificacao")
        IdentificacaoCapaCover identificacaoCapaCover,

        @JsonProperty(value = "carga")
        CargaCover cargaCover,

        @JsonProperty(value = "documentos")
        DocumentosCover documentosCover,

        @JsonProperty(value = "utilizacaoProcesso")
        UtilizacaoProcessoCover utilizacaoProcessoCover
) {

}