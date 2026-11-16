package com.souhenriza.duimp.model;

import tools.jackson.databind.JsonNode;

/** Leitura segura de campos de um {@link JsonNode}: campo ausente ou nulo vira {@code null}. */
final class Campos {

    private Campos() {
    }

    static String texto(JsonNode no, String... caminho) {
        JsonNode atual = percorrer(no, caminho);
        return atual.isMissingNode() || atual.isNull() ? null : atual.asString();
    }

    static Integer inteiro(JsonNode no, String... caminho) {
        JsonNode atual = percorrer(no, caminho);
        return atual.isNumber() ? atual.intValue() : null;
    }

    static JsonNode percorrer(JsonNode no, String... caminho) {
        JsonNode atual = no;
        for (String parte : caminho) {
            atual = atual.path(parte);
        }
        return atual;
    }
}
