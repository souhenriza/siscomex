package com.souhenriza.duimp;

import com.souhenriza.Siscomex;
import com.souhenriza.autenticacao.api.ClienteApi;
import com.souhenriza.duimp.model.importador.*;
import com.souhenriza.shared.PortalClient;
import com.souhenriza.shared.Query;
import com.souhenriza.shared.SiscomexGateway;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Consultas e envio de dados à API do Importador (DUIMP) do Portal Único Siscomex.
 * <p>
 * Cada método valida os argumentos, envia a consulta pelo {@link PortalClient} e converte a resposta em um
 * modelo. Erros de negócio e respostas inesperadas viram {@link DuimpException}; falhas de autenticação e de
 * comunicação continuam sendo {@code ExcecaoSiscomex}.
 */
public class DuimpClient {

    private static final String BASE = "/duimp-api/api/ext/duimp";
    private static final Pattern NI = Pattern.compile("\\d{1,14}");

    private final PortalClient portal;

    public DuimpClient(Siscomex siscomex) {
        this(Objects.requireNonNull(siscomex, "siscomex")::send);
    }

    public DuimpClient(SiscomexGateway gateway) {
        this.portal = new PortalClient(gateway, DuimpException::new);
    }

    /** Número da versão vigente de uma DUIMP já registrada. */
    public VersaoVigente consultarVersaoVigente(NumeroDuimp numero) {
        Objects.requireNonNull(numero, "numero");
        return VersaoVigente.de(portal.objeto(BASE + "/" + numero.valor() + "/versoes"));
    }

    /** Dados gerais (capa) de uma versão registrada da DUIMP. */
    public DuimpDados consultarDados(NumeroDuimp numero, VersaoDuimp versao) {
        return DuimpDados.de(portal.objeto(caminhoVersao(numero, versao)));
    }

    /** Valores calculados (tributos, frete, valor aduaneiro) de uma versão da DUIMP. */
    public ValoresCalculados consultarValoresCalculados(NumeroDuimp numero, VersaoDuimp versao) {
        return ValoresCalculados.de(portal.objeto(caminhoVersao(numero, versao) + "/valores-calculados"));
    }

    /** Faixa de itens de uma versão da DUIMP, a partir do intervalo informado. */
    public List<ItemDuimp> consultarItens(NumeroDuimp numero, VersaoDuimp versao, IntervaloItens intervalo) {
        Objects.requireNonNull(intervalo, "intervalo");
        String caminho = Query.novo()
                .param("inicial", intervalo.inicial())
                .param("tamanho", intervalo.tamanho())
                .aplicar(caminhoVersao(numero, versao) + "/itens");
        return portal.lista(caminho).stream().map(ItemDuimp::de).toList();
    }

    /** Um item específico de uma versão da DUIMP. */
    public ItemDuimp consultarItem(NumeroDuimp numero, VersaoDuimp versao, NumeroItem item) {
        return ItemDuimp.de(portal.objeto(caminhoItem(numero, versao, item)));
    }

    /** Valores calculados de um item específico. */
    public ValoresCalculadosItem consultarValoresCalculadosItem(NumeroDuimp numero, VersaoDuimp versao,
                                                                NumeroItem item) {
        return ValoresCalculadosItem.de(portal.objeto(caminhoItem(numero, versao, item) + "/valores-calculados"));
    }

    /** Resultado do diagnóstico de uma versão da DUIMP. */
    public DiagnosticoDuimp consultarDiagnostico(NumeroDuimp numero, VersaoDuimp versao) {
        String caminho = caminhoVersao(numero, versao) + "/diagnosticos";
        List<JsonNode> resultados = portal.lista(caminho);
        if (resultados.isEmpty()) {
            throw new DuimpException("O portal não devolveu diagnóstico para a DUIMP " + numero.valor()
                    + " versão " + versao.valor() + ".", 200, null, List.of());
        }
        return DiagnosticoDuimp.de(resultados.getFirst());
    }

    /** Chaves de acesso de uma lista de DUIMP. */
    public List<ChaveAcesso> consultarChavesAcesso(List<NumeroDuimp> numeros) {
        Objects.requireNonNull(numeros, "numeros");
        if (numeros.isEmpty()) {
            throw new DuimpException("Informe ao menos um número de DUIMP.");
        }
        Query query = Query.novo();
        for (NumeroDuimp numero : numeros) {
            query.param("numero-duimp", numero.valor());
        }
        return chavesDeLista(query.aplicar(BASE + "/chaves-acesso"));
    }

    /**
     * Chaves de acesso das DUIMP de um importador, registradas em um período.
     *
     * @param ni     número de identificação do importador (CPF ou CNPJ, somente dígitos)
     * @param inicio data inicial da pesquisa
     * @param fim    data final da pesquisa
     * @param offset deslocamento na lista de resultados (0 para o início)
     * @param limit  quantidade máxima de resultados
     */
    public List<ChaveAcesso> consultarChavesAcessoPorImportador(String ni, LocalDate inicio, LocalDate fim,
                                                                int offset, int limit) {
        Objects.requireNonNull(ni, "ni");
        Objects.requireNonNull(inicio, "inicio");
        Objects.requireNonNull(fim, "fim");
        if (!NI.matcher(ni).matches()) {
            throw new DuimpException("NI do importador inválido: devem ser de 1 a 14 dígitos.");
        }
        if (fim.isBefore(inicio)) {
            throw new DuimpException("A data final não pode ser anterior à data inicial.");
        }
        if (offset < 0 || limit < 1) {
            throw new DuimpException("offset deve ser maior ou igual a 0 e limit maior ou igual a 1.");
        }
        String caminho = Query.novo()
                .param("data-inicio", inicio)
                .param("data-termino", fim)
                .param("offset", offset)
                .param("limit", limit)
                .aplicar(BASE + "/chaves-acesso/importadores/" + ni);
        return chavesDeLista(caminho);

    }


    // ---------------------------------------------------------------- Método POST

    /**
     * Salva os dados gerais (capa) de uma nova DUIMP. A capa é validada antes do envio; se houver campo
     * obrigatório faltando, nada é enviado ao portal.
     *
     * @param duimp capa da DUIMP
     * @return número, versão e links da DUIMP criada
     * @throws DuimpValidacaoException se a capa não passar na validação local
     * @throws DuimpException          se o portal recusar a capa
     */
    public DadosDuimpSalvo salvarDadosGerais(Duimp duimp) {
        DuimpValidator.validar(duimp);
        return portal.enviar(BASE, duimp, DadosDuimpSalvo.class);
    }

    // ---------------------------------------------------------------- caminhos e conversão

    private static String caminhoVersao(NumeroDuimp numero, VersaoDuimp versao) {
        Objects.requireNonNull(numero, "numero");
        Objects.requireNonNull(versao, "versao");
        return BASE + "/" + numero.valor() + "/" + versao.valor();
    }

    private static String caminhoItem(NumeroDuimp numero, VersaoDuimp versao, NumeroItem item) {
        Objects.requireNonNull(item, "item");
        return caminhoVersao(numero, versao) + "/itens/" + item.valor();
    }

    /**
     * Lista de chaves de acesso. Aceita o array simples (importador) e o envelope {@code multiStatus} (lista de
     * DUIMP), em que cada item traz o campo {@code duimp}.
     */
    private List<ChaveAcesso> chavesDeLista(String caminho) {
        JsonNode no = portal.ler(caminho);
        List<ChaveAcesso> chaves = new ArrayList<>();
        if (no.isArray()) {
            no.forEach(item -> chaves.add(ChaveAcesso.de(item)));
        } else if (no.isObject() && no.path("multiStatus").isArray()) {
            for (JsonNode item : no.path("multiStatus")) {
                if (item.path("duimp").isObject()) {
                    chaves.add(ChaveAcesso.de(item.path("duimp")));
                }
            }
        } else {
            throw new DuimpException("Resposta do portal para " + ClienteApi.paraLog(caminho)
                    + " não tem o formato esperado (array JSON ou envelope multiStatus).", 200, null, List.of());
        }
        return List.copyOf(chaves);
    }
}
