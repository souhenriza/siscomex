package com.souhenriza.duimp.model.importador;

import com.souhenriza.shared.Campos;
import tools.jackson.databind.JsonNode;

/**
 * Chave de acesso de uma DUIMP.
 *
 * @param numero      número da DUIMP
 * @param chaveAcesso chave de acesso
 */
public record ChaveAcesso(String numero, String chaveAcesso) {

    public static ChaveAcesso de(JsonNode raw) {
        return new ChaveAcesso(Campos.texto(raw, "numero"), Campos.texto(raw, "chaveAcesso"));
    }
}
