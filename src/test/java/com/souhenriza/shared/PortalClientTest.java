package com.souhenriza.shared;

import com.souhenriza.autenticacao.api.RequisicaoSiscomex;
import com.souhenriza.autenticacao.api.RespostaSiscomex;
import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PortalClientTest {

    /** Gateway falso: registra as requisições e devolve a resposta configurada. */
    private static final class Portal implements SiscomexGateway {
        final List<RequisicaoSiscomex> enviadas = new ArrayList<>();
        RespostaSiscomex resposta = new RespostaSiscomex(200, "{}", Map.of());

        @Override
        public RespostaSiscomex send(RequisicaoSiscomex request) {
            enviadas.add(request);
            return resposta;
        }

        void responde(int status, String corpo) {
            resposta = new RespostaSiscomex(status, corpo, Map.of());
        }
    }

    private static final class Falha extends PortalException {
        Falha(String mensagem, int status, String codigo, List<ErroNegocio> erros, Throwable causa) {
            super(mensagem, status, codigo, erros, causa);
        }
    }

    record Moeda(String codigo, BigDecimal valor) {
    }

    record Corpo(String nome, Moeda moeda, String opcional) {
    }

    private static PortalClient client(Portal portal) {
        return new PortalClient(portal, Falha::new);
    }

    @Test
    void enviarFazPostComCorpoJsonSemCamposNulos() {
        Portal portal = new Portal();
        portal.responde(201, "{\"numero\":\"26BR00000000001\",\"versao\":1}");

        var resposta = client(portal).enviar("/api/recurso",
                new Corpo("teste", new Moeda("USD", new BigDecimal("30.12")), null));

        RequisicaoSiscomex enviada = portal.enviadas.getLast();
        assertEquals("POST", enviada.metodo());
        assertEquals("/api/recurso", enviada.caminho());
        assertEquals("{\"nome\":\"teste\",\"moeda\":{\"codigo\":\"USD\",\"valor\":30.12}}", enviada.corpo());
        assertEquals("26BR00000000001", resposta.path("numero").asString());
    }

    @Test
    void enviarConverteErroDeNegocioNaExcecaoDaApi() {
        Portal portal = new Portal();
        portal.responde(422, "{\"message\":\"Dados inválidos\",\"internal-code\":\"X1\","
                + "\"errors\":[{\"code\":\"DIMP-ER0001\",\"field\":\"carga\",\"message\":\"inválida\"}]}");

        Falha e = assertThrows(Falha.class, () -> client(portal).enviar("/api/recurso", new Corpo("a", null, null)));

        assertEquals(422, e.status());
        assertEquals("Dados inválidos (HTTP 422 em /api/recurso)", e.getMessage());
        assertEquals(List.of(new ErroNegocio("DIMP-ER0001", "carga", "inválida")), e.erros());
    }

    @Test
    void enviarRecusaCorpoNulo() {
        assertThrows(NullPointerException.class, () -> client(new Portal()).enviar("/api/recurso", null));
    }

    // ---------------------------------------------------------------- respostas tipadas

    record Identificacao(String numero, Integer versao) {
    }

    record Item(@JsonProperty("internal-code") String codigo, Identificacao identificacao, List<Moeda> moedas,
                @JacksonInject(PortalClient.RAW) @JsonIgnore JsonNode raw) {
    }

    record SemRaw(String nome) {
    }

    @Test
    void objetoTipadoMapeiaSubobjetosListasEInjetaRaw() {
        Portal portal = new Portal();
        String json = "{\"internal-code\":\"X1\",\"identificacao\":{\"numero\":\"N\",\"versao\":\"1\"},"
                + "\"moedas\":[{\"codigo\":\"USD\",\"valor\":30.12}],\"campoNovoDoPortal\":{\"a\":1}}";
        portal.responde(200, json);

        Item item = client(portal).objeto("/api/item", Item.class);

        assertEquals("GET", portal.enviadas.getLast().metodo());
        assertEquals("X1", item.codigo());
        assertEquals(new Identificacao("N", 1), item.identificacao());
        assertEquals(List.of(new Moeda("USD", new BigDecimal("30.12"))), item.moedas());
        assertEquals(1, item.raw().path("campoNovoDoPortal").path("a").intValue());
    }

    @Test
    void campoRawNoJsonNaoSubstituiARespostaInjetada() {
        Portal portal = new Portal();
        portal.responde(200, "{\"internal-code\":\"X1\",\"raw\":\"intruso\"}");

        Item item = client(portal).objeto("/api/item", Item.class);

        assertEquals("X1", item.raw().path("internal-code").asString());
    }

    @Test
    void tipoSemRawTambemFunciona() {
        Portal portal = new Portal();
        portal.responde(200, "{\"nome\":\"a\",\"outro\":1}");

        assertEquals(new SemRaw("a"), client(portal).objeto("/api/x", SemRaw.class));
    }

    @Test
    void listaTipadaConverteCadaElementoComSeuRaw() {
        Portal portal = new Portal();
        portal.responde(200, "[{\"internal-code\":\"A\"},{\"internal-code\":\"B\"}]");

        List<Item> itens = client(portal).lista("/api/itens", Item.class);

        assertEquals(List.of("A", "B"), itens.stream().map(Item::codigo).toList());
        assertEquals("B", itens.get(1).raw().path("internal-code").asString());
    }

    @Test
    void enviarTipadoFazPostEConverteResposta() {
        Portal portal = new Portal();
        portal.responde(201, "{\"identificacao\":{\"numero\":\"N\",\"versao\":1}}");

        Item item = client(portal).enviar("/api/item", new Corpo("a", null, null), Item.class);

        assertEquals("POST", portal.enviadas.getLast().metodo());
        assertEquals("{\"nome\":\"a\"}", portal.enviadas.getLast().corpo());
        assertEquals(new Identificacao("N", 1), item.identificacao());
    }

    @Test
    void formatoErradoParaOTipoGeraExcecaoDaApiComCausa() {
        Portal portal = new Portal();
        portal.responde(200, "{\"identificacao\":\"deveria ser objeto\"}");

        Falha e = assertThrows(Falha.class, () -> client(portal).objeto("/api/item", Item.class));

        assertEquals("Resposta do portal para /api/item não pôde ser lida como Item.", e.getMessage());
        assertEquals(200, e.status());
        assertNotNull(e.getCause());
    }

    @Test
    void objetoTipadoRecusaArray() {
        Portal portal = new Portal();
        portal.responde(200, "[]");

        assertThrows(Falha.class, () -> client(portal).objeto("/api/item", Item.class));
    }

    @Test
    void lerContinuaFazendoGetSemCorpo() {
        Portal portal = new Portal();
        portal.responde(200, "{\"ok\":true}");

        client(portal).ler("/api/recurso");

        RequisicaoSiscomex enviada = portal.enviadas.getLast();
        assertEquals("GET", enviada.metodo());
        assertNull(enviada.corpo());
    }
}
