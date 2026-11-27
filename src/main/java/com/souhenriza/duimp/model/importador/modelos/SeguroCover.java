package com.souhenriza.duimp.model.importador.modelos;

import java.math.BigDecimal;

public record SeguroCover(
        String codigoMoedaNegociada,
        BigDecimal valorMoedaNegociada
) {
}
