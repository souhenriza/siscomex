package com.souhenriza.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes unitários de {@link PortalResponse}: status, headers sem diferenciar maiúsculas e mascaramento. */
class PortalResponseTest {

    @Test
    @DisplayName("isSuccess é verdadeiro somente para a faixa 200 a 299")
    void isSuccessOnlyFor2xx() {
        assertFalse(response(199).isSuccess());
        assertTrue(response(200).isSuccess());
        assertTrue(response(207).isSuccess(), "207 (multi-status) está na faixa 2xx");
        assertTrue(response(299).isSuccess());
        assertFalse(response(300).isSuccess());
        assertFalse(response(401).isSuccess());
        assertFalse(response(500).isSuccess());
    }

    @Test
    @DisplayName("header() devolve o primeiro valor, sem diferenciar maiúsculas, e vazio quando ausente")
    void headerReturnsFirstValueCaseInsensitively() {
        PortalResponse response = new PortalResponse(200, "", Map.of("Set-Token", List.of("jwt-1", "jwt-2")));

        assertEquals("jwt-1", response.header("set-token").orElseThrow());
        assertTrue(response.header("X-Inexistente").isEmpty());
    }

    @Test
    @DisplayName("headerValues() devolve todos os valores na ordem recebida, ou lista vazia")
    void headerValuesReturnsAllValues() {
        PortalResponse response = new PortalResponse(200, "", Map.of("Set-Cookie", List.of("a=1", "b=2")));

        assertEquals(List.of("a=1", "b=2"), response.headerValues("SET-COOKIE"));
        assertEquals(List.of(), response.headerValues("Ausente"));
    }

    @Test
    @DisplayName("Lista de valores devolvida é não modificável")
    void headerValuesAreUnmodifiable() {
        PortalResponse response = new PortalResponse(200, "", Map.of("A", List.of("1")));

        assertThrows(UnsupportedOperationException.class, () -> response.headerValues("A").add("2"));
    }

    @Test
    @DisplayName("firstValues() devolve um valor por header, o primeiro de cada um")
    void firstValuesKeepsOnlyFirstValue() {
        PortalResponse response = new PortalResponse(200, "", Map.of("A", List.of("1", "2"), "B", List.of("x")));

        Map<String, String> primeiros = response.firstValues();

        assertEquals(Map.of("A", "1", "B", "x"), Map.copyOf(primeiros));
        assertEquals("1", primeiros.get("a"));
    }

    @Test
    @DisplayName("Headers nulos equivalem a nenhum header; nome nulo é recusado")
    void nullHeadersMeanNone() {
        PortalResponse response = new PortalResponse(204, "", null);

        assertTrue(response.header("X").isEmpty());
        assertTrue(response.firstValues().isEmpty());
        assertThrows(NullPointerException.class, () -> response.header(null));
        assertThrows(NullPointerException.class, () -> response.headerValues(null));
    }

    @Test
    @DisplayName("Status e corpo são devolvidos como recebidos")
    void statusAndBodyAreReturned() {
        PortalResponse response = new PortalResponse(422, "{\"erro\":1}", Map.of());

        assertEquals(422, response.status());
        assertEquals("{\"erro\":1}", response.body());
    }

    @Test
    @DisplayName("toString mascara Set-Token e X-CSRF-Token, mas mostra os demais headers")
    void toStringMasksSessionHeaders() {
        PortalResponse response = new PortalResponse(200, "{}", Map.of(
                "Set-Token", List.of("jwt-secreto"),
                "X-CSRF-Token", List.of("csrf-secreto"),
                "Content-Type", List.of("application/json")));

        String texto = response.toString();

        assertFalse(texto.contains("jwt-secreto"), texto);
        assertFalse(texto.contains("csrf-secreto"), texto);
        assertTrue(texto.contains("***"), texto);
        assertTrue(texto.contains("application/json"), texto);
        assertTrue(texto.contains("status=200"), texto);
    }

    private static PortalResponse response(int status) {
        return new PortalResponse(status, "", Map.of());
    }
}
