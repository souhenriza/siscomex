package com.souhenriza.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes unitários de {@link PortalRequest}: montagem, imutabilidade e codificação. Não usam rede. */
class PortalRequestTest {

    private static final String PATH = "/duimp-api/api/ext/duimp/21BR00000012345/1";

    @Test
    @DisplayName("Cada fábrica define o método correspondente, sem corpo e sem parâmetros")
    void factoriesSetMethodWithoutBodyOrParams() {
        assertEquals(HttpMethod.GET, PortalRequest.get(PATH).method());
        assertEquals(HttpMethod.POST, PortalRequest.post(PATH).method());
        assertEquals(HttpMethod.PUT, PortalRequest.put(PATH).method());
        assertEquals(HttpMethod.DELETE, PortalRequest.delete(PATH).method());
        assertEquals(HttpMethod.PUT, PortalRequest.of(HttpMethod.PUT, PATH).method());

        PortalRequest get = PortalRequest.get(PATH);
        assertEquals(PATH, get.path());
        assertEquals(PATH, get.target());
        assertTrue(get.body().isEmpty());
        assertTrue(get.headers().isEmpty());
    }

    @Test
    @DisplayName("Rejeita caminho que não começa com '/'")
    void rejectsPathWithoutLeadingSlash() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> PortalRequest.get("duimp-api/api"));
        assertTrue(e.getMessage().contains("duimp-api/api"));
    }

    @Test
    @DisplayName("Rejeita caminho e método nulos")
    void rejectsNullPathAndMethod() {
        assertThrows(NullPointerException.class, () -> PortalRequest.get(null));
        assertThrows(NullPointerException.class, () -> PortalRequest.of(null, PATH));
    }

    @Test
    @DisplayName("Parâmetros são anexados em ordem, codificados em UTF-8 e repetidos quando o nome se repete")
    void paramsAreEncodedInOrderAndRepeatable() {
        PortalRequest request = PortalRequest.get("/due/api/ext/due/consultar")
                .param("numero", "17BR 0055&562808")
                .param("numero-duimp", "A")
                .param("numero-duimp", "B");

        assertEquals("/due/api/ext/due/consultar?numero=17BR%200055%26562808&numero-duimp=A&numero-duimp=B",
                request.target());
    }

    @Test
    @DisplayName("Parâmetro com valor nulo é ignorado e a requisição permanece igual")
    void nullParamIsIgnored() {
        PortalRequest original = PortalRequest.get(PATH);

        PortalRequest request = original.param("offset", null);

        assertEquals(PATH, request.target());
        assertEquals(PATH, original.target());
    }

    @Test
    @DisplayName("Valor de parâmetro não textual é convertido com String.valueOf")
    void nonStringParamIsConverted() {
        assertEquals("/x?limit=50&ativo=true", PortalRequest.get("/x").param("limit", 50).param("ativo", true).target());
    }

    @Test
    @DisplayName("Parâmetro com nome vazio ou nulo é recusado")
    void rejectsBlankOrNullParamName() {
        assertThrows(IllegalArgumentException.class, () -> PortalRequest.get(PATH).param(" ", "x"));
        assertThrows(NullPointerException.class, () -> PortalRequest.get(PATH).param(null, "x"));
    }

    @Test
    @DisplayName("Header é substituído quando o nome se repete sem diferenciar maiúsculas")
    void headerReplacesCaseInsensitively() {
        PortalRequest request = PortalRequest.get(PATH)
                .header("X-CSRF-Token", "primeiro")
                .header("x-csrf-token", "segundo");

        assertEquals(1, request.headers().size());
        assertEquals("segundo", request.headers().get("X-CSRF-Token"));
    }

    @Test
    @DisplayName("Header com nome vazio é recusado e valor nulo lança NullPointerException")
    void rejectsInvalidHeader() {
        assertThrows(IllegalArgumentException.class, () -> PortalRequest.get(PATH).header("", "x"));
        assertThrows(NullPointerException.class, () -> PortalRequest.get(PATH).header("Authorization", null));
    }

    @Test
    @DisplayName("headers(Map) aplica cada entrada e aceita nulo como nenhum header")
    void headersMapAppliesEntries() {
        PortalRequest request = PortalRequest.get(PATH)
                .headers(Map.of("Role-Type", "IMPEXP"))
                .headers(null);

        assertEquals("IMPEXP", request.headers().get("role-type"));
        assertEquals(1, request.headers().size());
    }

    @Test
    @DisplayName("Mapa de headers devolvido é não modificável")
    void headersMapIsUnmodifiable() {
        PortalRequest request = PortalRequest.get(PATH).header("Accept", "x");

        assertThrows(UnsupportedOperationException.class, () -> request.headers().put("Y", "z"));
    }

    @Test
    @DisplayName("Corpo JSON é guardado e reportado por body(); valor nulo é recusado")
    void jsonBodyIsStored() {
        PortalRequest request = PortalRequest.post(PATH).json("{\"a\":1}");

        assertEquals("{\"a\":1}", request.body().orElseThrow());
        assertThrows(NullPointerException.class, () -> PortalRequest.post(PATH).json(null));
    }

    @Test
    @DisplayName("Métodos de alteração devolvem nova instância e não mudam a original")
    void builderMethodsAreImmutable() {
        PortalRequest original = PortalRequest.get(PATH);

        PortalRequest changed = original.param("a", 1).header("H", "v").json("{}");

        assertFalse(original.body().isPresent());
        assertTrue(original.headers().isEmpty());
        assertEquals(PATH, original.target());
        assertTrue(changed.body().isPresent());
        assertEquals(1, changed.headers().size());
    }

    @Test
    @DisplayName("toString mostra método e caminho, mas nunca headers nem corpo")
    void toStringHidesHeadersAndBody() {
        PortalRequest request = PortalRequest.post(PATH)
                .header("Authorization", "jwt-secreto")
                .json("{\"cpf\":\"corpo-secreto\"}");

        String texto = request.toString();

        assertTrue(texto.contains("POST"));
        assertTrue(texto.contains(PATH));
        assertFalse(texto.contains("jwt-secreto"));
        assertFalse(texto.contains("corpo-secreto"));
    }
}
