package com.souhenriza.duimp.model.importador;

import com.souhenriza.duimp.model.importador.modelos.UtilizacaoProcessoFreteCover;

public record UtilizacaoProcessoCover(
        String indicadorUsoProcessoValoresCalculados,
        UtilizacaoProcessoFreteCover frete
){}