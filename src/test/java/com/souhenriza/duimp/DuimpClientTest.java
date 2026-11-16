package com.souhenriza.duimp;

import com.souhenriza.auth.api.SiscomexRequest;
import com.souhenriza.auth.api.SiscomexResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuimpClientTest {

    private static final NumeroDuimp NUMERO = NumeroDuimp.of("21BR00000012345");
    private static final VersaoDuimp VERSAO = VersaoDuimp.of(2);
    private static final String BASE = "/duimp-api/api/ext/duimp";

    /** Gateway falso: registra as requisições e devolve a resposta configurada. */
    private static final class Portal implements SiscomexGateway {
        final List<SiscomexRequest> enviadas = new ArrayList<>();
        SiscomexResponse resposta = new SiscomexResponse(200, "{}", Map.of());

        @Override
        public SiscomexResponse send(SiscomexRequest request) {
            enviadas.add(request);
            return resposta;
        }

        void responde(int status, String corpo) {
            resposta = new SiscomexResponse(status, corpo, Map.of());
        }

        String ultimoCaminho() {
            return enviadas.getLast().path();
        }
    }

    @Test
    void versaoVigenteUsaCaminhoDeVersoes() {
        Portal portal = new Portal();
        portal.responde(200, "{\"numero\":\"21BR00000012345\",\"versao\":\"2\",\"dataRegistro\":\"2021-05-25T15:53:18-0300\"}");

        var vigente = new DuimpClient(portal).consultarVersaoVigente(NUMERO);

        assertEquals(BASE + "/21BR00000012345/versoes", portal.ultimoCaminho());
        assertEquals("21BR00000012345", vigente.numero());
        assertEquals("2021-05-25T15:53:18-0300", vigente.dataRegistro());
    }

    @Test
    void dadosGeraisExtraemIdentificacaoEPreservamOCorpo() {
        Portal portal = new Portal();
        portal.responde(200, "{\"identificacao\":{\"numero\":\"21BR00000012345\",\"versao\":2},"
                + "\"carga\":{\"tipoIdentificacaoCarga\":\"CE\"}}");

        var dados = new DuimpClient(portal).consultarDados(NUMERO, VERSAO);

        assertEquals(BASE + "/21BR00000012345/2", portal.ultimoCaminho());
        assertEquals(2, dados.versao());
        assertEquals("CE", dados.raw().path("carga").path("tipoIdentificacaoCarga").asString());
    }

    @Test
    void faixaDeItensEnviaInicialETamanho() {
        Portal portal = new Portal();
        portal.responde(200, "[{\"status\":\"ATIVO\",\"identificacao\":{\"numero\":\"21BR00000012345\","
                + "\"versao\":2,\"numeroItem\":1}},{\"status\":\"ATIVO\",\"identificacao\":{\"numeroItem\":2}}]");

        var itens = new DuimpClient(portal).consultarItens(NUMERO, VERSAO, new IntervaloItens(1, 100));

        assertEquals(BASE + "/21BR00000012345/2/itens?inicial=1&tamanho=100", portal.ultimoCaminho());
        assertEquals(2, itens.size());
        assertEquals(1, itens.getFirst().numeroItem());
        assertEquals("ATIVO", itens.getFirst().status());
    }

    @Test
    void itemEspecificoUsaNumeroDoItemNoCaminho() {
        Portal portal = new Portal();
        portal.responde(200, "{\"status\":\"ATIVO\",\"identificacao\":{\"numeroItem\":7}}");

        new DuimpClient(portal).consultarItem(NUMERO, VERSAO, NumeroItem.of(7));

        assertEquals(BASE + "/21BR00000012345/2/itens/7", portal.ultimoCaminho());
    }

    @Test
    void valoresCalculadosDoItemUsamSubcaminho() {
        Portal portal = new Portal();
        portal.responde(200, "{\"identificacao\":{\"numeroItem\":3}}");

        var valores = new DuimpClient(portal).consultarValoresCalculadosItem(NUMERO, VERSAO, NumeroItem.of(3));

        assertEquals(BASE + "/21BR00000012345/2/itens/3/valores-calculados", portal.ultimoCaminho());
        assertEquals(3, valores.numeroItem());
    }

    @Test
    void diagnosticoLeOPrimeiroElementoDoArray() {
        Portal portal = new Portal();
        portal.responde(200, "[{\"identificacao\":{\"numero\":\"19BR00000004677\",\"versao\":1},"
                + "\"situacaoDuimp\":\"REGISTRADA_AGUARDANDO_CANAL\","
                + "\"diagnostico\":{\"situacao\":\"PROCESSADO_OK\",\"dataGeracao\":\"2021-05-25T15:53:18-0300\"}}]");

        var diagnostico = new DuimpClient(portal).consultarDiagnostico(NUMERO, VERSAO);

        assertEquals(BASE + "/21BR00000012345/2/diagnosticos", portal.ultimoCaminho());
        assertEquals("PROCESSADO_OK", diagnostico.situacaoDiagnostico());
        assertEquals("REGISTRADA_AGUARDANDO_CANAL", diagnostico.situacaoDuimp());
    }

    @Test
    void chavesDeAcessoRepetemOParametroNumeroDuimp() {
        Portal portal = new Portal();
        portal.responde(200, "{\"message\":\"ok\",\"multiStatus\":["
                + "{\"code\":200,\"duimp\":{\"numero\":\"21BR00000012345\",\"chaveAcesso\":\"21ASW000000879\"}},"
                + "{\"code\":200,\"duimp\":{\"numero\":\"21BR00000012346\",\"chaveAcesso\":\"21ASW000000880\"}}]}");

        var chaves = new DuimpClient(portal).consultarChavesAcesso(List.of(
                NumeroDuimp.of("21BR00000012345"), NumeroDuimp.of("21BR00000012346")));

        assertEquals(BASE + "/chaves-acesso?numero-duimp=21BR00000012345&numero-duimp=21BR00000012346",
                portal.ultimoCaminho());
        assertEquals(2, chaves.size());
        assertEquals("21ASW000000880", chaves.get(1).chaveAcesso());
    }

    @Test
    void chavesDeAcessoPorImportadorEnviaPeriodoEPaginacao() {
        Portal portal = new Portal();
        portal.responde(200, "[{\"numero\":\"20BR00000012345\",\"chaveAcesso\":\"21ASW000000879\"}]");

        var chaves = new DuimpClient(portal).consultarChavesAcessoPorImportador("00000000000191",
                LocalDate.of(2021, 3, 1), LocalDate.of(2021, 6, 10), 0, 100);

        assertEquals(BASE + "/chaves-acesso/importadores/00000000000191?data-inicio=2021-03-01"
                + "&data-termino=2021-06-10&offset=0&limit=100", portal.ultimoCaminho());
        assertEquals(1, chaves.size());
    }

    @Test
    void erro422ExtraiCodigoInternoEErrosDeNegocio() {
        Portal portal = new Portal();
        portal.responde(422, "{\"internal-code\":\"DIMP-ER0001\",\"message\":\"Erro de negócio.\","
                + "\"errors\":[{\"code\":\"DIMP-ER9999\",\"field\":\"numero\",\"message\":\"Detalhe.\"}]}");

        DuimpException e = assertThrows(DuimpException.class,
                () -> new DuimpClient(portal).consultarDados(NUMERO, VERSAO));

        assertTrue(e.isBusinessError());
        assertFalse(e.isRetryable());
        assertEquals("DIMP-ER0001", e.codigoInterno());
        assertEquals(1, e.erros().size());
        assertEquals("DIMP-ER9999", e.erros().getFirst().code());
        assertEquals("numero", e.erros().getFirst().field());
    }

    @Test
    void erro404MarcaRecursoNaoEncontrado() {
        Portal portal = new Portal();
        portal.responde(404, "");

        DuimpException e = assertThrows(DuimpException.class,
                () -> new DuimpClient(portal).consultarDados(NUMERO, VERSAO));

        assertTrue(e.isNotFound());
        assertEquals(404, e.status());
    }

    @Test
    void erro500PodeSerRepetido() {
        Portal portal = new Portal();
        portal.responde(500, "{\"message\":\"falha\"}");

        DuimpException e = assertThrows(DuimpException.class,
                () -> new DuimpClient(portal).consultarDados(NUMERO, VERSAO));

        assertTrue(e.isRetryable());
    }

    @Test
    void corpoQueNaoEJsonGeraDuimpExceptionComCausa() {
        Portal portal = new Portal();
        portal.responde(200, "<html>portal fora</html>");

        DuimpException e = assertThrows(DuimpException.class,
                () -> new DuimpClient(portal).consultarDados(NUMERO, VERSAO));

        assertEquals(200, e.status());
        assertTrue(e.getCause() != null);
    }

    @Test
    void formatoInesperadoEmListaGeraDuimpException() {
        Portal portal = new Portal();
        portal.responde(200, "{\"numero\":\"x\"}");

        assertThrows(DuimpException.class,
                () -> new DuimpClient(portal).consultarItens(NUMERO, VERSAO, IntervaloItens.padrao()));
    }

    @Test
    void niComCaracteresEstranhosNaoChegaAoPortal() {
        Portal portal = new Portal();

        assertThrows(IllegalArgumentException.class, () -> new DuimpClient(portal)
                .consultarChavesAcessoPorImportador("1/../2", LocalDate.of(2021, 3, 1),
                        LocalDate.of(2021, 6, 10), 0, 100));
        assertTrue(portal.enviadas.isEmpty());
    }

    @Test
    void periodoInvertidoNaoChegaAoPortal() {
        Portal portal = new Portal();

        assertThrows(IllegalArgumentException.class, () -> new DuimpClient(portal)
                .consultarChavesAcessoPorImportador("191", LocalDate.of(2021, 6, 10),
                        LocalDate.of(2021, 3, 1), 0, 100));
        assertTrue(portal.enviadas.isEmpty());
    }
}
