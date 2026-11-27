package com.souhenriza.due;

import com.souhenriza.Siscomex;
import com.souhenriza.due.model.*;
import com.souhenriza.shared.PortalClient;
import com.souhenriza.shared.Query;
import com.souhenriza.shared.SiscomexGateway;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Consultas à API do Exportador (DUE) do Portal Único Siscomex. Somente leitura.
 * <p>
 * Cada método valida os argumentos, envia a consulta pelo {@link PortalClient} e converte a resposta em um modelo.
 * Erros de negócio e respostas inesperadas viram {@link DueException}; falhas de autenticação e de comunicação
 * continuam sendo {@code ExcecaoSiscomex}.
 */
public class DueClient {

    private static final String BASE = "/due/api/ext/due";
    /** RUC: NAA seguido de 32 dígitos, 35 caracteres no total (formato da documentação da DUE). */
    private static final Pattern RUC = Pattern.compile("\\d[A-Z]{2}\\d{32}");

    private final PortalClient portal;

    public DueClient(Siscomex siscomex) {
        this(Objects.requireNonNull(siscomex, "siscomex")::send);
    }

    public DueClient(SiscomexGateway gateway) {
        this.portal = new PortalClient(gateway, DueException::new);
    }

    /** Links para as DUEs vinculadas à chave de acesso de uma NF-e. */
    public List<LinksDue> consultarLinkDues(ChaveDeAcessoNFe chaveDeAcessoNFe) {
        Objects.requireNonNull(chaveDeAcessoNFe, "chave de acesso nfe");
        String caminho = Query.novo()
                .param("nota-fiscal", chaveDeAcessoNFe.chaveDeAcessoNFe())
                .aplicar(BASE);
        return portal.lista(caminho).stream().map(LinksDue::de).toList();
    }

    /**
     * Dados resumidos de uma DUE. Informe o número da DUE <b>ou</b> o RUC; o outro deve ser {@code null}.
     */
    public DadosResumidosDue consultarDadosResumidosDue(NumeroDue numeroDue, String ruc) {
        if (numeroDue == null && ruc == null) {
            throw new DueException("Deve haver pelo menos um número a ser consultado: utilize o número da DUE ou o RUC.");
        }
        if (numeroDue != null && ruc != null) {
            throw new DueException("Informe apenas um número a ser consultado: o número da DUE ou o RUC.");
        }
        String identificador = numeroDue != null ? numeroDue.valor() : validarRuc(ruc);
        String caminho = Query.novo()
                .param("numero", identificador)
                .aplicar(BASE + "/consultarDadosResumidosDUE");
        return DadosResumidosDue.de(portal.objeto(caminho));
    }

    /** Dados completos de uma DUE, pelo número. */
    public DueDados consultarDadosPorNumeroDue(NumeroDue numeroDue) {
        Objects.requireNonNull(numeroDue, "numero da DUE");
        return DueDados.de(portal.objeto(BASE + "/numero-da-due/" + numeroDue.valor()));
    }

    /** Dados completos de uma DUE, pelo RUC (referência única de carga). */
    public DueDados consultarDadosPorNumeroRUC(String ruc) {
        return DueDados.de(portal.objeto(BASE + "/numero-da-ruc/" + validarRuc(ruc)));
    }

    /** Atos concessórios de drawback do tipo isenção vinculados a uma DUE. */
    public List<AtosConcessoriosIsencao> consultarAtosConcessoriosIsencao(NumeroDue numeroDue, Integer numeroItemDeDue,
                                                                          Integer offset, Integer limit,
                                                                          Integer numeroAtoConcessionario,
                                                                          Integer numeroDoItemDoAtoAcessorio,
                                                                          String cnpjBeneficiario,
                                                                          AtoConcessionarioOrdenacao ordenacao,
                                                                          AtoConcessionarioTipoOrdenacao tipoOrdenacao) {
        return atosConcessorios("isencao", AtosConcessoriosIsencao::de, numeroDue,
                new FiltroAtos(numeroItemDeDue, offset, limit, numeroAtoConcessionario, numeroDoItemDoAtoAcessorio,
                        cnpjBeneficiario, ordenacao, tipoOrdenacao));
    }

    /** Atos concessórios de drawback do tipo suspensão vinculados a uma DUE. */
    public List<AtosConcessionarosSuspensao> consultarAtosConcessionariosSuspensao(NumeroDue numeroDue,
                                                                                   Integer numeroItemDeDue, Integer offset,
                                                                                   Integer limit,
                                                                                   Integer numeroAtoConcessionario,
                                                                                   Integer numeroDoItemDoAtoAcessorio,
                                                                                   String cnpjBeneficiario,
                                                                                   AtoConcessionarioOrdenacao ordenacao,
                                                                                   AtoConcessionarioTipoOrdenacao tipoOrdenacao) {
        return atosConcessorios("suspensao", AtosConcessionarosSuspensao::de, numeroDue,
                new FiltroAtos(numeroItemDeDue, offset, limit, numeroAtoConcessionario, numeroDoItemDoAtoAcessorio,
                        cnpjBeneficiario, ordenacao, tipoOrdenacao));
    }
    /**
     * Exigências fiscais de uma DUE
     */
    public List<ExigenciasFiscais> exigenciasFiscais(NumeroDue numeroDue) {
        Objects.requireNonNull(numeroDue, "numero da DUE");

        return portal.lista(BASE + "/" + numeroDue.valor() + "/exigencias-fiscais").stream()
                .map(ExigenciasFiscais::de).toList();
    }

    // ---------------------------------------------------------------- caminhos e conversão

    /** Filtros da consulta de atos concessórios, com os nomes de parâmetro da documentação. */
    private record FiltroAtos(Integer numeroItemDeDue, Integer offset, Integer limit, Integer numero, Integer numeroDoItem,
                              String cnpjDoBeneficiario, AtoConcessionarioOrdenacao ordenacao,
                              AtoConcessionarioTipoOrdenacao tipoOrdenacao) {
    }

    private <T> List<T> atosConcessorios(String tipo, Function<JsonNode, T> conversor, NumeroDue numeroDue,
                                         FiltroAtos filtro) {
        Objects.requireNonNull(numeroDue, "numero da DUE");
        String caminho = Query.novo()
                .param("numero-item-de-due", filtro.numeroItemDeDue())
                .param("offset", filtro.offset())
                .param("limit", filtro.limit())
                .param("numero", filtro.numero())
                .param("numero-do-item", filtro.numeroDoItem())
                .param("cnpj-do-beneficiario", filtro.cnpjDoBeneficiario())
                .param("ordenacao", filtro.ordenacao())
                .param("tipoDeOrdenacao", filtro.tipoOrdenacao())
                .aplicar(BASE + "/" + numeroDue.valor() + "/drawback/" + tipo + "/atos-concessorios");
        return portal.lista(caminho).stream().map(conversor).toList();
    }

    private static String validarRuc(String ruc) {
        Objects.requireNonNull(ruc, "registro unico de carga");
        if (!RUC.matcher(ruc).matches()) {
            throw new DueException("O RUC deve ter 35 caracteres no formato NAANNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNN.");
        }
        return ruc;
    }
}
