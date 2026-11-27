package com.souhenriza.shared;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CamposTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final LocalDateTime ESPERADA = LocalDateTime.of(2021, 5, 25, 15, 53, 18);

    private static JsonNode com(String valor) {
        return JSON.createObjectNode().set("d", JSON.createObjectNode().put("valor", valor));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2021-05-25T15:53:18-0300",   // formato do portal
            "2021-05-25T15:53:18-03:00",
            "2021-05-25T15:53:18Z",
            "2021-05-25T15:53:18",
            " 2021-05-25T15:53:18-0300 "
    })
    void dataHoraAceitaFormatosDoPortalEIsoMantendoAHoraInformada(String valor) {
        assertEquals(ESPERADA, Campos.dataHora(com(valor), "d", "valor"));
    }

    @Test
    void dataHoraComFracaoDeSegundos() {
        assertEquals(ESPERADA.withNano(123_000_000),
                Campos.dataHora(com("2021-05-25T15:53:18.123-0300"), "d", "valor"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "25/05/2021 15:53", "2021-05-25", "ontem", "2021-13-25T15:53:18"})
    void dataHoraInvalidaViraNull(String valor) {
        assertNull(Campos.dataHora(com(valor), "d", "valor"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2021-05-25", "2021-05-25T15:53:18-0300", "2021-05-25T23:59:59-03:00"})
    void dataAceitaDataPuraOuDataHora(String valor) {
        assertEquals(LocalDate.of(2021, 5, 25), Campos.data(com(valor), "d", "valor"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "25/05/2021", "2021-02-30"})
    void dataInvalidaViraNull(String valor) {
        assertNull(Campos.data(com(valor), "d", "valor"));
    }

    @Test
    void campoAusenteOuNuloViraNull() {
        JsonNode no = JSON.createObjectNode().putNull("nulo");
        assertNull(Campos.dataHora(no, "ausente"));
        assertNull(Campos.dataHora(no, "nulo"));
        assertNull(Campos.data(no, "ausente", "mais", "fundo"));
        assertNull(Campos.data(no, "nulo"));
    }
}
