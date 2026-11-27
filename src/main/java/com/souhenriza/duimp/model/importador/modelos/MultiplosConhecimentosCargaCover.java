package com.souhenriza.duimp.model.importador.modelos;

import java.util.List;

public record MultiplosConhecimentosCargaCover(
        /**
         * Aceita apenas SIM, NAO e NA.
         */
        String indicadorUsoMultiplosConhecimentosCarga,
        List<MultiplosConhecimentosCargaReferenciadaCover> cargasReferenciadas
) {
}
