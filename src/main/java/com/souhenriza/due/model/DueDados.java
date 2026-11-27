package com.souhenriza.due.model;

import com.souhenriza.shared.Campos;
import tools.jackson.databind.JsonNode;

/**
 *
 * @param chaveDeAcesso
 * @param dataDeCriacao
 * @param dataDeRegistro
 * @param raw
 */
public record DueDados(
        String chaveDeAcesso,
        String dataDeCriacao,
        String dataDeRegistro,
        JsonNode raw
) {

    public static DueDados de(JsonNode raw) {
        return new DueDados(Campos.texto(raw, "chaveDeAcesso"), Campos.texto(raw, "dataDeCriacao"), Campos.texto(raw, "dataDeRegistro"), raw);
    }
}
