package com.souhenriza.duimp;

import com.souhenriza.Siscomex;
import com.souhenriza.auth.api.SiscomexRequest;
import com.souhenriza.auth.api.SiscomexResponse;
import com.souhenriza.duimp.model.ChaveAcesso;
import com.souhenriza.duimp.model.DiagnosticoDuimp;
import com.souhenriza.duimp.model.DuimpDados;
import com.souhenriza.duimp.model.ItemDuimp;
import com.souhenriza.duimp.model.ValoresCalculados;
import com.souhenriza.duimp.model.ValoresCalculadosItem;
import com.souhenriza.duimp.model.VersaoVigente;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Consultas à API do Importador (DUIMP) do Portal Único Siscomex. Somente leitura.
 * <p>
 * Cada método valida os argumentos, envia a requisição autenticada pelo {@link SiscomexGateway} e converte a
 * resposta em um modelo. Erros de negócio e respostas inesperadas viram {@link DuimpException}; falhas de
 * autenticação e de comunicação continuam sendo {@code SiscomexException}.
 */
public class DuimpClient {

    private static final String BASE = "/duimp-api/api/ext/duimp";
    private static final Pattern NI = Pattern.compile("\\d{1,14}");
    private static final int MULTI_STATUS = 207;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final SiscomexGateway gateway;

    public DuimpClient(Siscomex siscomex) {
        this(Objects.requireNonNull(siscomex, "siscomex")::send);
    }

    public DuimpClient(SiscomexGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
    }

    /** Número da versão vigente de uma DUIMP já registrada. */
    public VersaoVigente consultarVersaoVigente(NumeroDuimp numero) {
        Objects.requireNonNull(numero, "numero");
        return VersaoVigente.de(objeto(BASE + "/" + numero + "/versoes"));
    }

    /** Dados gerais (capa) de uma versão registrada da DUIMP. */
    public DuimpDados consultarDados(NumeroDuimp numero, VersaoDuimp versao) {
        return DuimpDados.de(objeto(caminhoVersao(numero, versao)));
    }

    /** Valores calculados (tributos, frete, valor aduaneiro) de uma versão da DUIMP. */
    public ValoresCalculados consultarValoresCalculados(NumeroDuimp numero, VersaoDuimp versao) {
        return ValoresCalculados.de(objeto(caminhoVersao(numero, versao) + "/valores-calculados"));
    }

    /** Faixa de itens de uma versão da DUIMP, a partir do intervalo informado. */
    public List<ItemDuimp> consultarItens(NumeroDuimp numero, VersaoDuimp versao, IntervaloItens intervalo) {
        Objects.requireNonNull(intervalo, "intervalo");
        String caminho = caminhoVersao(numero, versao) + "/itens"
                + "?inicial=" + intervalo.inicial() + "&tamanho=" + intervalo.tamanho();
        List<ItemDuimp> itens = new ArrayList<>();
        for (JsonNode no : lista(caminho)) {
            itens.add(ItemDuimp.de(no));
        }
        return List.copyOf(itens);
    }

    /** Um item específico de uma versão da DUIMP. */
    public ItemDuimp consultarItem(NumeroDuimp numero, VersaoDuimp versao, NumeroItem item) {
        return ItemDuimp.de(objeto(caminhoItem(numero, versao, item)));
    }

    /** Valores calculados de um item específico. */
    public ValoresCalculadosItem consultarValoresCalculadosItem(NumeroDuimp numero, VersaoDuimp versao,
                                                                NumeroItem item) {
        return ValoresCalculadosItem.de(objeto(caminhoItem(numero, versao, item) + "/valores-calculados"));
    }

    /** Resultado do diagnóstico de uma versão da DUIMP. */
    public DiagnosticoDuimp consultarDiagnostico(NumeroDuimp numero, VersaoDuimp versao) {
        String caminho = caminhoVersao(numero, versao) + "/diagnosticos";
        List<JsonNode> resultados = lista(caminho);
        if (resultados.isEmpty()) {
            throw new DuimpException("O portal não devolveu diagnóstico para a DUIMP " + numero + " versão "
                    + versao.valor() + ".", 200, null, List.of());
        }
        return DiagnosticoDuimp.de(resultados.getFirst());
    }

    /** Chaves de acesso de uma lista de DUIMP. */
    public List<ChaveAcesso> consultarChavesAcesso(List<NumeroDuimp> numeros) {
        Objects.requireNonNull(numeros, "numeros");
        if (numeros.isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos um número de DUIMP.");
        }
        StringBuilder query = new StringBuilder();
        for (NumeroDuimp numero : numeros) {
            query.append(query.isEmpty() ? "?" : "&")
                    .append("numero-duimp=").append(codificar(numero.valor()));
        }
        return chavesDeLista(BASE + "/chaves-acesso" + query);
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
            throw new IllegalArgumentException("NI do importador inválido: devem ser de 1 a 14 dígitos.");
        }
        if (fim.isBefore(inicio)) {
            throw new IllegalArgumentException("A data final não pode ser anterior à data inicial.");
        }
        if (offset < 0 || limit < 1) {
            throw new IllegalArgumentException("offset deve ser maior ou igual a 0 e limit maior ou igual a 1.");
        }
        String caminho = BASE + "/chaves-acesso/importadores/" + ni
                + "?data-inicio=" + inicio + "&data-termino=" + fim + "&offset=" + offset + "&limit=" + limit;
        return chavesDeLista(caminho);
    }

    // ---------------------------------------------------------------- envio e conversão

    private static String caminhoVersao(NumeroDuimp numero, VersaoDuimp versao) {
        Objects.requireNonNull(numero, "numero");
        Objects.requireNonNull(versao, "versao");
        return BASE + "/" + numero + "/" + versao.valor();
    }

    private static String caminhoItem(NumeroDuimp numero, VersaoDuimp versao, NumeroItem item) {
        Objects.requireNonNull(item, "item");
        return caminhoVersao(numero, versao) + "/itens/" + item.valor();
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Envia a consulta e devolve um objeto JSON. */
    private JsonNode objeto(String caminho) {
        JsonNode no = ler(caminho);
        if (!no.isObject()) {
            throw formatoInesperado(caminho, "objeto JSON");
        }
        return no;
    }

    /** Envia a consulta e devolve os elementos de um array JSON. */
    private List<JsonNode> lista(String caminho) {
        JsonNode no = ler(caminho);
        if (!no.isArray()) {
            throw formatoInesperado(caminho, "array JSON");
        }
        List<JsonNode> elementos = new ArrayList<>();
        no.forEach(elementos::add);
        return elementos;
    }

    /**
     * Lista de chaves de acesso. Aceita o array simples (importador) e o envelope {@code multiStatus} (lista de
     * DUIMP), em que cada item traz o campo {@code duimp}.
     */
    private List<ChaveAcesso> chavesDeLista(String caminho) {
        JsonNode no = ler(caminho);
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
            throw formatoInesperado(caminho, "array JSON ou envelope multiStatus");
        }
        return List.copyOf(chaves);
    }

    /** Envia a consulta ao portal, converte erros em {@link DuimpException} e devolve o corpo lido. */
    private JsonNode ler(String caminho) {
        SiscomexResponse resposta = gateway.send(SiscomexRequest.get(caminho));
        if (!resposta.isSuccess() && resposta.status() != MULTI_STATUS) {
            throw erro(resposta, caminho);
        }
        String corpo = resposta.body();
        if (corpo == null || corpo.isBlank()) {
            throw new DuimpException("O portal respondeu HTTP " + resposta.status() + " sem corpo para " + caminho + ".",
                    resposta.status(), null, List.of());
        }
        try {
            return JSON.readTree(corpo);
        } catch (JacksonException e) {
            throw new DuimpException("Resposta do portal para " + caminho + " não é JSON válido.",
                    resposta.status(), null, List.of(), e);
        }
    }

    private static DuimpException erro(SiscomexResponse resposta, String caminho) {
        String codigo = null;
        List<ErroNegocio> erros = new ArrayList<>();
        String mensagem = null;
        try {
            JsonNode corpo = JSON.readTree(resposta.body() == null ? "" : resposta.body());
            codigo = texto(corpo, "internal-code");
            mensagem = texto(corpo, "message");
            for (JsonNode e : corpo.path("errors")) {
                erros.add(new ErroNegocio(texto(e, "code"), texto(e, "field"), texto(e, "message")));
            }
        } catch (JacksonException | NullPointerException ignorado) {
            // Corpo de erro que não é JSON: segue apenas com o status.
        }
        String descricao = mensagem != null ? mensagem : "Falha na consulta da DUIMP";
        return new DuimpException(descricao + " (HTTP " + resposta.status() + " em " + caminho + ")",
                resposta.status(), codigo, erros);
    }

    private static String texto(JsonNode no, String campo) {
        JsonNode valor = no.path(campo);
        return valor.isMissingNode() || valor.isNull() ? null : valor.asString();
    }

    private static DuimpException formatoInesperado(String caminho, String esperado) {
        return new DuimpException("Resposta do portal para " + caminho + " não tem o formato esperado (" + esperado + ").",
                200, null, List.of());
    }
}
