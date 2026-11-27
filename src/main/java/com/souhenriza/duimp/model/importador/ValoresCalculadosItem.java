package com.souhenriza.duimp.model.importador;

import com.souhenriza.shared.Campos;
import tools.jackson.databind.JsonNode;

/**
 * Valores calculados de um item da DUIMP.
 *
 * @param numero     número da DUIMP
 * @param versao     versão da DUIMP
 * @param numeroItem número do item
 * @param raw        resposta completa do portal (condição de venda, mercadoria, acréscimos e deduções, etc.)
 */
public record ValoresCalculadosItem(String numero, Integer versao, Integer numeroItem, JsonNode raw) {

    public static ValoresCalculadosItem de(JsonNode raw) {
        return new ValoresCalculadosItem(Campos.texto(raw, "identificacao", "numero"),
                Campos.inteiro(raw, "identificacao", "versao"),
                Campos.inteiro(raw, "identificacao", "numeroItem"), raw);
    }
}
