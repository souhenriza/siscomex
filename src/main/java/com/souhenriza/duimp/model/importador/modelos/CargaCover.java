package com.souhenriza.duimp.model.importador.modelos;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"tipoIdentificacaoCarga", "identificacao", "unidadeDeclarada", "motivoSituacaoEspecial",
                        "seguro", "frete", "paisProcedencia", "multiplosConhecimentosCarga", "indicadores", "recintoEntregaMotivo"})
public record CargaCover(
        @JsonProperty("tipoIdentificacaoCarga")
        TipoIdentificacaoCarga tipoIdentificacaoCarga,

        String identificacao,

        @JsonProperty("unidadeDeclarada")
        UnidadeDeclaradaCover unidadeDeclaradaCover,

        String motivoSituacaoEspecial,

        @JsonProperty("seguro")
        SeguroCover seguroCover,

        @JsonProperty("frete")
        FreteCover freteCover,

        @JsonProperty("paisProcedencia")
        PaisProcedenciaCover paisProcedenciaCover,

        @JsonProperty("multiplosConhecimentosCarga")
        MultiplosConhecimentosCargaCover multiplosConhecimentosCargaCover,

        @JsonProperty("indicadores")
        CargaIndicadoresCover cargaIndicadoresCover,

        String recintoEntregaMotivo

) {
}
