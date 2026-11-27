package com.souhenriza.due.model;

import com.souhenriza.shared.Campos;
import tools.jackson.databind.JsonNode;

/**
 * Dados resumidos de uma DUE
 * @param numeroDue número da DUE
 * @param numeroRuc número da RUC
 * @param raw resposta completa do portal
 */
public record DadosResumidosDue(NumeroDue numeroDue, String numeroRuc, JsonNode raw) {
    public static DadosResumidosDue de(JsonNode raw) {
        return new DadosResumidosDue(NumeroDue.of(Campos.texto(raw, "numeroDUE")), Campos.texto(raw, "numeroRUC"),
                raw);
    }
}
