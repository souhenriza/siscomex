package com.souhenriza.duimp;

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.souhenriza.duimp.model.importador.IdentificacaoDuimpSalva;
import com.souhenriza.duimp.model.importador.LinkDuimp;
import com.souhenriza.shared.PortalClient;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * Resposta do portal ao salvar os dados gerais (capa) de uma DUIMP.
 *
 * @param codigoInterno código informado pelo portal ({@code internal-code}), quando houver
 * @param mensagem      mensagem informada pelo portal, quando houver
 * @param identificacao número e versão da DUIMP criada
 * @param links         links para os recursos relacionados (vazio quando o portal não informa)
 * @param raw           resposta completa do portal
 */
public record DadosDuimpSalvo(
        @JsonProperty("internal-code") String codigoInterno,
        @JsonProperty("message") String mensagem,
        IdentificacaoDuimpSalva identificacao,
        List<LinkDuimp> links,
        @JacksonInject(PortalClient.RAW) @JsonIgnore JsonNode raw) {

    public DadosDuimpSalvo {
        links = links == null ? List.of() : List.copyOf(links);
    }

    /** Número da DUIMP criada, ou {@code null} se o portal não informou. */
    public String numero() {
        return identificacao == null ? null : identificacao.numero();
    }

    /** Versão criada, ou {@code null} se o portal não informou. */
    public Integer versao() {
        return identificacao == null ? null : identificacao.versao();
    }
}
