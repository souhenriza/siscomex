package com.souhenriza.duimp.model;

import tools.jackson.databind.JsonNode;

/**
 * Dados gerais (capa) de uma versão da DUIMP. Os campos de identificação são tipados; o restante da capa
 * (importador, carga, frete, seguro, país de procedência, etc.) está em {@code raw}.
 *
 * @param numero número da DUIMP
 * @param versao versão consultada
 * @param raw    resposta completa do portal
 */
public record DuimpDados(String numero, Integer versao, JsonNode raw) {

    public static DuimpDados de(JsonNode raw) {
        return new DuimpDados(Campos.texto(raw, "identificacao", "numero"),
                Campos.inteiro(raw, "identificacao", "versao"), raw);
    }
}
