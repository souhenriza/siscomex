package com.souhenriza.duimp;

import com.souhenriza.duimp.model.importador.Duimp;
import com.souhenriza.duimp.model.importador.modelos.CargaCover;
import com.souhenriza.duimp.model.importador.modelos.DocumentosCover;
import com.souhenriza.duimp.model.importador.modelos.DocumentosInstrucaoCover;
import com.souhenriza.duimp.model.importador.modelos.FreteCover;
import com.souhenriza.duimp.model.importador.modelos.IdentificacaoCapaCover;
import com.souhenriza.duimp.model.importador.modelos.ImportadorCover;
import com.souhenriza.duimp.model.importador.modelos.SeguroCover;

import java.util.ArrayList;
import java.util.List;

/**
 * Valida a capa da DUIMP antes do envio ao portal: campos obrigatórios e objetos que, quando informados,
 * exigem seus próprios campos. Percorre o objeto inteiro e reporta todas as violações de uma vez.
 * <p>
 * Texto em branco conta como ausente.
 */
public final class DuimpValidator {

    private DuimpValidator() {
    }

    /**
     * @throws DuimpValidacaoException com todas as violações, se houver alguma
     */
    public static void validar(Duimp duimp) {
        List<String> violacoes = new ArrayList<>();
        if (duimp == null) {
            violacoes.add("duimp: obrigatório");
        } else {
            identificacao(duimp.identificacaoCapaCover(), violacoes);
            carga(duimp.cargaCover(), violacoes);
            documentos(duimp.documentosCover(), violacoes);
        }
        if (!violacoes.isEmpty()) {
            throw new DuimpValidacaoException(violacoes);
        }
    }

    private static void identificacao(IdentificacaoCapaCover identificacao, List<String> v) {
        if (!obrigatorio(identificacao, "identificacao", v)) {
            return;
        }
        ImportadorCover importador = identificacao.importadorCover();
        if (obrigatorio(importador, "identificacao.importador", v)) {
            obrigatorio(importador.tipoImportador(), "identificacao.importador.tipoImportador", v);
            obrigatorio(importador.ni(), "identificacao.importador.ni", v);
        }
    }

    private static void carga(CargaCover carga, List<String> v) {
        if (!obrigatorio(carga, "carga", v)) {
            return;
        }
        if (obrigatorio(carga.unidadeDeclaradaCover(), "carga.unidadeDeclarada", v)) {
            obrigatorio(carga.unidadeDeclaradaCover().codigo(), "carga.unidadeDeclarada.codigo", v);
        }

        SeguroCover seguro = carga.seguroCover();
        if (seguro != null) {
            obrigatorio(seguro.codigoMoedaNegociada(), "carga.seguro.codigoMoedaNegociada", v);
            obrigatorio(seguro.valorMoedaNegociada(), "carga.seguro.valorMoedaNegociada", v);
        }

        FreteCover frete = carga.freteCover();
        if (frete != null) {
            obrigatorio(frete.codigoMoedaNegociada(), "carga.frete.codigoMoedaNegociada", v);
            obrigatorio(frete.valorMoedaNegociada(), "carga.frete.valorMoedaNegociada", v);
        }
    }

    private static void documentos(DocumentosCover documentos, List<String> v) {
        if (!obrigatorio(documentos, "documentos", v)) {
            return;
        }
        List<DocumentosInstrucaoCover> lista = documentos.documentosInstrucao();
        if (!obrigatorio(lista, "documentos.documentosInstrucao", v)) {
            return;
        }
        for (int i = 0; i < lista.size(); i++) {
            String caminho = "documentos.documentosInstrucao[" + i + "]";
            DocumentosInstrucaoCover doc = lista.get(i);
            if (!obrigatorio(doc, caminho, v)) {
                continue;
            }
            if (obrigatorio(doc.tipo(), caminho + ".tipo", v)) {
                obrigatorio(doc.tipo().codigo(), caminho + ".tipo.codigo", v);
            }
            obrigatorio(doc.palavrasChave(), caminho + ".palavrasChave", v);
        }
    }

    /** Registra a violação se o valor faltar; devolve {@code true} se o valor está presente. */
    private static boolean obrigatorio(Object valor, String caminho, List<String> v) {
        boolean ausente = valor == null || (valor instanceof String s && s.isBlank());
        if (ausente) {
            v.add(caminho + ": obrigatório");
        }
        return !ausente;
    }
}
