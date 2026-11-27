package com.souhenriza.duimp.model.importador.modelos;

import java.util.List;

public record DocumentosInstrucaoCover(
        TipoDocumentoInstrucaoCover tipo,
        List<PalavraChaveDocumentoInstrucaoCover> palavrasChave
) {
}
