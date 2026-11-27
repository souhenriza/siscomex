package com.souhenriza.duimp;

import com.souhenriza.duimp.model.importador.Duimp;
import com.souhenriza.duimp.model.importador.modelos.CargaCover;
import com.souhenriza.duimp.model.importador.modelos.DocumentosCover;
import com.souhenriza.duimp.model.importador.modelos.DocumentosInstrucaoCover;
import com.souhenriza.duimp.model.importador.modelos.FreteCover;
import com.souhenriza.duimp.model.importador.modelos.IdentificacaoCapaCover;
import com.souhenriza.duimp.model.importador.modelos.ImportadorCover;
import com.souhenriza.duimp.model.importador.modelos.PalavraChaveDocumentoInstrucaoCover;
import com.souhenriza.duimp.model.importador.modelos.SeguroCover;
import com.souhenriza.duimp.model.importador.modelos.TipoDocumentoInstrucaoCover;
import com.souhenriza.duimp.model.importador.modelos.TipoImportador;
import com.souhenriza.duimp.model.importador.modelos.UnidadeDeclaradaCover;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DuimpValidatorTest {

    private static final ImportadorCover IMPORTADOR = new ImportadorCover(TipoImportador.CNPJ, "00055555000130");
    private static final UnidadeDeclaradaCover UNIDADE = new UnidadeDeclaradaCover("7912001");
    private static final DocumentosInstrucaoCover DOC = new DocumentosInstrucaoCover(
            new TipoDocumentoInstrucaoCover("99"),
            List.of(new PalavraChaveDocumentoInstrucaoCover(33, "9999.99.99")));

    private static CargaCover carga(UnidadeDeclaradaCover unidade, SeguroCover seguro, FreteCover frete) {
        return new CargaCover(null, null, unidade, null, seguro, frete, null, null, null, null);
    }

    private static Duimp duimp(ImportadorCover importador, CargaCover carga, DocumentosCover documentos) {
        return new Duimp(new IdentificacaoCapaCover(importador, null), carga, documentos, null);
    }

    private static Duimp minima() {
        return duimp(IMPORTADOR, carga(UNIDADE, null, null), new DocumentosCover(List.of(DOC), null, null));
    }

    private static List<String> violacoes(Duimp duimp) {
        return assertThrows(DuimpValidacaoException.class, () -> DuimpValidator.validar(duimp)).violacoes();
    }

    @Test
    void aceitaCapaComApenasOsObrigatorios() {
        assertDoesNotThrow(() -> DuimpValidator.validar(minima()));
    }

    @Test
    void aceitaSeguroEFreteCompletos() {
        var moeda = new BigDecimal("30.12");
        Duimp d = duimp(IMPORTADOR,
                carga(UNIDADE, new SeguroCover("USD", moeda), new FreteCover("USD", moeda)),
                new DocumentosCover(List.of(DOC), null, null));
        assertDoesNotThrow(() -> DuimpValidator.validar(d));
    }

    @Test
    void recusaDuimpNula() {
        assertEquals(List.of("duimp: obrigatório"), violacoes(null));
    }

    @Test
    void recusaBlocosRaizAusentes() {
        assertEquals(List.of("identificacao: obrigatório", "carga: obrigatório", "documentos: obrigatório"),
                violacoes(new Duimp(null, null, null, null)));
    }

    @Test
    void recusaCamposDoImportador() {
        assertEquals(List.of("identificacao.importador: obrigatório"),
                violacoes(duimp(null, carga(UNIDADE, null, null), new DocumentosCover(List.of(DOC), null, null))));

        assertEquals(List.of("identificacao.importador.tipoImportador: obrigatório",
                        "identificacao.importador.ni: obrigatório"),
                violacoes(duimp(new ImportadorCover(null, "  "), carga(UNIDADE, null, null),
                        new DocumentosCover(List.of(DOC), null, null))));
    }

    @Test
    void recusaUnidadeDeclarada() {
        var docs = new DocumentosCover(List.of(DOC), null, null);
        assertEquals(List.of("carga.unidadeDeclarada: obrigatório"),
                violacoes(duimp(IMPORTADOR, carga(null, null, null), docs)));
        assertEquals(List.of("carga.unidadeDeclarada.codigo: obrigatório"),
                violacoes(duimp(IMPORTADOR, carga(new UnidadeDeclaradaCover(null), null, null), docs)));
    }

    @Test
    void recusaSeguroEFreteIncompletos() {
        Duimp d = duimp(IMPORTADOR,
                carga(UNIDADE, new SeguroCover(null, BigDecimal.ONE), new FreteCover("USD", null)),
                new DocumentosCover(List.of(DOC), null, null));
        assertEquals(List.of("carga.seguro.codigoMoedaNegociada: obrigatório",
                "carga.frete.valorMoedaNegociada: obrigatório"), violacoes(d));
    }

    @Test
    void recusaDocumentosDeInstrucao() {
        assertEquals(List.of("documentos.documentosInstrucao: obrigatório"),
                violacoes(duimp(IMPORTADOR, carga(UNIDADE, null, null), new DocumentosCover(null, null, null))));

        var lista = Arrays.asList(
                null,
                new DocumentosInstrucaoCover(null, null),
                new DocumentosInstrucaoCover(new TipoDocumentoInstrucaoCover(""), List.of()));
        assertEquals(List.of(
                        "documentos.documentosInstrucao[0]: obrigatório",
                        "documentos.documentosInstrucao[1].tipo: obrigatório",
                        "documentos.documentosInstrucao[1].palavrasChave: obrigatório",
                        "documentos.documentosInstrucao[2].tipo.codigo: obrigatório"),
                violacoes(duimp(IMPORTADOR, carga(UNIDADE, null, null), new DocumentosCover(lista, null, null))));
    }

    @Test
    void mensagemListaTodasAsViolacoes() {
        var e = assertThrows(DuimpValidacaoException.class,
                () -> DuimpValidator.validar(new Duimp(null, null, null, null)));
        assertEquals("Capa da DUIMP inválida:\n - identificacao: obrigatório\n - carga: obrigatório"
                + "\n - documentos: obrigatório", e.getMessage());
    }
}
