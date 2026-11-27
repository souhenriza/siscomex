package com.souhenriza.duimp.model.importador.modelos;

import java.util.List;

public record DocumentosCover(
        List<DocumentosInstrucaoCover> documentosInstrucao,
        List<ProcessoDocumentoInstrucaoCover> processos,
        List<DeclaracaoExportacaoEstrangeiraCover> declaracoesExportacaoEstrangeira
) {
}
