package com.souhenriza.duimp.model.importador.modelos;

import java.math.BigDecimal;

public record UtilizacaoProcessoFreteCover(
        String indicadorUsoProcesso,
        String justificativaUsoProcesso,
        BigDecimal valorFrete,
        BigDecimal valorAfrmm,
        BigDecimal valorTum
) {
}
