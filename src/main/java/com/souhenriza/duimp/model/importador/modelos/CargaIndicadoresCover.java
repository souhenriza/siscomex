package com.souhenriza.duimp.model.importador.modelos;

public record CargaIndicadoresCover(

        /**
         * Aceita apenas SIM, NAO e NA.
         */
        String entregaFracionada,
        /**
         * Aceita apenas SIM, NAO e NA.
         */
        String descargaDireta
) {
}
