package com.souhenriza.duimp.model;

import tools.jackson.databind.JsonNode;

/**
 * Resultado do diagnóstico de uma versão da DUIMP.
 *
 * @param numero           número da DUIMP
 * @param versao           versão da DUIMP
 * @param situacaoDuimp    situação da DUIMP, por exemplo {@code REGISTRADA_AGUARDANDO_CANAL}
 * @param situacaoDiagnostico situação do diagnóstico, por exemplo {@code PROCESSADO_OK}
 * @param dataGeracao      data de geração do diagnóstico
 * @param raw              resposta completa do portal, incluindo {@code diagnostico.resultado[]}
 */
public record DiagnosticoDuimp(String numero, Integer versao, String situacaoDuimp,
                               String situacaoDiagnostico, String dataGeracao, JsonNode raw) {

    public static DiagnosticoDuimp de(JsonNode raw) {
        return new DiagnosticoDuimp(Campos.texto(raw, "identificacao", "numero"),
                Campos.inteiro(raw, "identificacao", "versao"),
                Campos.texto(raw, "situacaoDuimp"),
                Campos.texto(raw, "diagnostico", "situacao"),
                Campos.texto(raw, "diagnostico", "dataGeracao"), raw);
    }
}
