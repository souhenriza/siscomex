package com.souhenriza.due.model;

import com.souhenriza.shared.Campos;
import tools.jackson.databind.JsonNode;

/** Ato concessório de drawback do tipo suspensão. Os campos seguem o exemplo de resposta da documentação. */
public record AtosConcessionarosSuspensao(
        String numero,
        Integer quantidadeExportada,
        JsonNode raw
) {

    public static AtosConcessionarosSuspensao de(JsonNode raw) {
        return new AtosConcessionarosSuspensao(Campos.texto(raw, "numero"), Campos.inteiro(raw, "quantidadeExportada"),
                raw);
    }
}
