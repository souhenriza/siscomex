package com.souhenriza.duimp.model;

import tools.jackson.databind.JsonNode;

/**
 * Versão vigente de uma DUIMP (resposta de {@code /versoes}).
 *
 * @param numero       número da DUIMP
 * @param versao       versão vigente
 * @param dataRegistro data do registro, como informada pelo portal
 * @param raw          resposta completa do portal
 */
public record VersaoVigente(String numero, Integer versao, String dataRegistro, JsonNode raw) {

    public static VersaoVigente de(JsonNode raw) {
        return new VersaoVigente(Campos.texto(raw, "numero"), Campos.inteiro(raw, "versao"),
                Campos.texto(raw, "dataRegistro"), raw);
    }
}
