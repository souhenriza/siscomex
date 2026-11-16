package com.souhenriza.duimp.model;

import tools.jackson.databind.JsonNode;

/**
 * Valores calculados de uma versão da DUIMP: tributos, frete, valor aduaneiro e adições.
 *
 * @param numero número da DUIMP
 * @param versao versão consultada
 * @param raw    resposta completa do portal ({@code utilizacaoProcesso}, {@code adicoes},
 *               {@code mercadoriaLocalEmbarque}, {@code tributosCalculados})
 */
public record ValoresCalculados(String numero, Integer versao, JsonNode raw) {

    public static ValoresCalculados de(JsonNode raw) {
        return new ValoresCalculados(Campos.texto(raw, "identificacao", "numero"),
                Campos.inteiro(raw, "identificacao", "versao"), raw);
    }
}
