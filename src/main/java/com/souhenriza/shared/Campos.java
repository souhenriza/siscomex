package com.souhenriza.shared;

import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;

/** Leitura segura de campos de um {@link JsonNode}: campo ausente ou nulo vira {@code null}. */
public final class Campos {

    public Campos() {
    }

    public static String texto(JsonNode no, String... caminho) {
        JsonNode current = percorrer(no, caminho);
        return current.isMissingNode() || current.isNull() ? null : current.asString();
    }

    /** Inteiro informado como número ou como texto (o portal usa os dois, por exemplo {@code "versao": "1"}). */
    public static Integer inteiro(JsonNode no, String... caminho) {
        JsonNode current = percorrer(no, caminho);
        if (current.isNumber()) {
            return current.intValue();
        }
        if (current.isString()) {
            try {
                return Integer.valueOf(current.asString().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Data e hora como informadas pelo portal, por exemplo {@code 2021-05-25T15:53:18-0300}. O fuso, quando
     * presente, é descartado: o resultado é a hora local informada, sem conversão para outro fuso. Aceita fuso com
     * ou sem {@code :} ({@code -03:00}, {@code -0300}, {@code Z}), fração de segundos ou nenhum fuso. Texto fora
     * desses formatos vira {@code null}.
     */
    public static LocalDateTime dataHora(JsonNode no, String... caminho) {
        return paraDataHora(texto(no, caminho));
    }

    /**
     * Data informada pelo portal, por exemplo {@code 2021-05-25}. Se o campo trouxer data e hora (ver
     * {@link #dataHora}), devolve só a data. Texto fora desses formatos vira {@code null}.
     */
    public static LocalDate data(JsonNode no, String... caminho) {
        String valor = texto(no, caminho);
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(valor.trim());
        } catch (DateTimeParseException e) {
            LocalDateTime dataHora = paraDataHora(valor);
            return dataHora == null ? null : dataHora.toLocalDate();
        }
    }

    private static final DateTimeFormatter DATA_HORA = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .optionalStart().appendOffset("+HH:MM", "Z").optionalEnd()
            .optionalStart().appendOffset("+HHMM", "Z").optionalEnd()
            .toFormatter();

    private static LocalDateTime paraDataHora(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return DATA_HORA.parse(valor.trim(), LocalDateTime::from);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static JsonNode percorrer(JsonNode no, String... caminho) {
        JsonNode current = no;
        for (String parte : caminho) {
            current = current.path(parte);
        }
        return current;
    }
}
