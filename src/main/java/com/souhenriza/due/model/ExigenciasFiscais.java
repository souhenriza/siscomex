package com.souhenriza.due.model;

import com.souhenriza.shared.Campos;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.Map;

public record ExigenciasFiscais(
        LocalDateTime dataLiberacao,
        LocalDateTime dataRegistro,
        JsonNode raw
) {

    public static ExigenciasFiscais de(JsonNode raw) {
        return new ExigenciasFiscais(Campos.dataHora(raw, "dataLiberacao"), Campos.dataHora(raw, "dataRegistro"), raw);
    }

}
