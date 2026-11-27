package com.souhenriza.due;

import com.souhenriza.autenticacao.api.RequisicaoSiscomex;
import com.souhenriza.autenticacao.api.RespostaSiscomex;
import com.souhenriza.due.model.*;
import com.souhenriza.shared.SiscomexGateway;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DueClientTest {

    private static final String BASE = "/due/api/ext/due";
    private static final NumeroDue NUMERO = NumeroDue.of("17BR0055562808");
    private static final String RUC = "1BR" + "0".repeat(32);

    /** Gateway falso: registra as requisições e devolve a resposta configurada. */
    private static final class Portal implements SiscomexGateway {
        final List<RequisicaoSiscomex> enviadas = new ArrayList<>();
        RespostaSiscomex resposta = new RespostaSiscomex(200, "[]", Map.of());

        @Override
        public RespostaSiscomex send(RequisicaoSiscomex request) {
            enviadas.add(request);
            return resposta;
        }

        String ultimoCaminho() {
            return enviadas.getLast().caminho();
        }
    }

    @Test
    void linksDaDueUsamChaveDaNfeComoParametro() {
        Portal portal = new Portal();
        new DueClient(portal).consultarLinkDues(new ChaveDeAcessoNFe("35200614200166000187550010000000011000000010"));

        assertEquals(BASE + "?nota-fiscal=35200614200166000187550010000000011000000010", portal.ultimoCaminho());
    }

    @Test
    void exigenciasFiscaisUsaONumeroRealDaDue() {
        Portal portal = new Portal();
        new DueClient(portal).exigenciasFiscais(NUMERO);

        assertEquals(BASE + "/17BR0055562808/exigencias-fiscais", portal.ultimoCaminho());
    }

    @Test
    void numeroDaDueVaiParaORecursoSemMascara() {
        Portal portal = new Portal();
        portal.resposta = new RespostaSiscomex(200, "{}", Map.of());
        new DueClient(portal).consultarDadosPorNumeroDue(NUMERO);

        assertEquals(BASE + "/numero-da-due/17BR0055562808", portal.ultimoCaminho());
    }

    @Test
    void resumidosAceitaNumeroDaDueOuRuc() {
        Portal portal = new Portal();
        portal.resposta = new RespostaSiscomex(200, "{\"numeroDUE\":\"17BR0055562808\"}", Map.of());
        DueClient client = new DueClient(portal);

        client.consultarDadosResumidosDue(NUMERO, null);
        assertEquals(BASE + "/consultarDadosResumidosDUE?numero=17BR0055562808", portal.ultimoCaminho());

        client.consultarDadosResumidosDue(null, RUC);
        assertEquals(BASE + "/consultarDadosResumidosDUE?numero=" + RUC, portal.ultimoCaminho());
    }

    @Test
    void resumidosRecusaNenhumOuDois() {
        DueClient client = new DueClient(new Portal());
        assertThrows(DueException.class, () -> client.consultarDadosResumidosDue(null, null));
        assertThrows(DueException.class, () -> client.consultarDadosResumidosDue(NUMERO, RUC));
    }

    @Test
    void rucForaDoFormatoEhRecusadoAntesDeIrAoPortal() {
        Portal portal = new Portal();
        DueClient client = new DueClient(portal);
        assertThrows(DueException.class, () -> client.consultarDadosPorNumeroRUC("123"));
        assertThrows(DueException.class, () -> client.consultarDadosResumidosDue(null, "123"));
        assertTrue(portal.enviadas.isEmpty());
    }

    @Test
    void atosDeIsencaoUsamOCaminhoDaDueEOsNomesDaDocumentacao() {
        Portal portal = new Portal();
        new DueClient(portal).consultarAtosConcessoriosIsencao(NUMERO, 1, 0, 49, 12345678, 2, "12345678000190",
                AtoConcessionarioOrdenacao.NUMERO, AtoConcessionarioTipoOrdenacao.ASC);

        assertEquals(BASE + "/17BR0055562808/drawback/isencao/atos-concessorios"
                        + "?numero-item-de-due=1&offset=0&limit=49&numero=12345678&numero-do-item=2"
                        + "&cnpj-do-beneficiario=12345678000190&ordenacao=NUMERO&tipoDeOrdenacao=ASC",
                portal.ultimoCaminho());
    }

    @Test
    void atosDeSuspensaoUsamOCaminhoDeSuspensao() {
        Portal portal = new Portal();
        new DueClient(portal).consultarAtosConcessionariosSuspensao(NUMERO, 1, 0, 49, 0, 0, null, null, null);

        assertEquals(BASE + "/17BR0055562808/drawback/suspensao/atos-concessorios"
                + "?numero-item-de-due=1&offset=0&limit=49&numero=0&numero-do-item=0", portal.ultimoCaminho());
    }

    @Test
    void numeroDaDueComFormatoAntigoEhRecusado() {
        assertThrows(DueException.class, () -> NumeroDue.of("12345678901234"));
    }
}
