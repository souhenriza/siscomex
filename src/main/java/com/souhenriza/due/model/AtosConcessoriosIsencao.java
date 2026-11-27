package com.souhenriza.due.model;

import com.souhenriza.shared.Campos;
import tools.jackson.databind.JsonNode;

public record AtosConcessoriosIsencao(
        String numero,
        Integer quantidadeExportada,
        JsonNode raw

) {

    public static AtosConcessoriosIsencao de(JsonNode raw) {
        return new AtosConcessoriosIsencao(Campos.texto(raw, "numero"), Campos.inteiro(raw, "quantidadeExportada"), raw);
    }
}
