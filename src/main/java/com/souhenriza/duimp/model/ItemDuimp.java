package com.souhenriza.duimp.model;

import tools.jackson.databind.JsonNode;

/**
 * Um item de uma versão da DUIMP. Identificação e situação são tipadas; produto, mercadoria, fabricante e
 * demais blocos estão em {@code raw}.
 *
 * @param numero     número da DUIMP
 * @param versao     versão da DUIMP
 * @param numeroItem número do item
 * @param status     situação do item, como {@code ATIVO}
 * @param raw        resposta completa do portal
 */
public record ItemDuimp(String numero, Integer versao, Integer numeroItem, String status, JsonNode raw) {

    public static ItemDuimp de(JsonNode raw) {
        return new ItemDuimp(Campos.texto(raw, "identificacao", "numero"),
                Campos.inteiro(raw, "identificacao", "versao"),
                Campos.inteiro(raw, "identificacao", "numeroItem"),
                Campos.texto(raw, "status"), raw);
    }
}
