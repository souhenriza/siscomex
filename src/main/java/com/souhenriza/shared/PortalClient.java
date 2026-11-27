package com.souhenriza.shared;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.souhenriza.autenticacao.api.ClienteApi;
import com.souhenriza.autenticacao.api.RequisicaoSiscomex;
import com.souhenriza.autenticacao.api.RespostaSiscomex;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.InjectableValues;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Transporte comum às APIs do Portal Único: envia a requisição autenticada (GET ou POST) pelo {@link SiscomexGateway}, converte
 * erros HTTP e corpos inválidos em exceções e devolve o JSON lido. Cada client informa a exceção que deve ser
 * lançada, para que os erros continuem tipados por API.
 */
public final class PortalClient {

    /** Status HTTP em que o portal devolve resultado parcial (multi-status). Não é erro. */
    public static final int MULTI_STATUS = 207;

    /** Nome do valor injetado com a resposta completa: {@code @JacksonInject(PortalClient.RAW)}. */
    public static final String RAW = "raw";

    /**
     * Lê as respostas e escreve os corpos de requisição. Campos nulos não vão para o JSON enviado; campos novos
     * que o portal passar a devolver não quebram a leitura.
     */
    private static final JsonMapper JSON = JsonMapper.builder()
            .changeDefaultPropertyInclusion(v -> v.withValueInclusion(JsonInclude.Include.NON_NULL))
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /** Cria a exceção específica de cada API a partir dos dados da falha. */
    @FunctionalInterface
    public interface FabricaErro {
        PortalException criar(String mensagem, int status, String codigoInterno, List<ErroNegocio> erros,
                              Throwable causa);
    }

    private final SiscomexGateway gateway;
    private final FabricaErro fabrica;

    public PortalClient(SiscomexGateway gateway, FabricaErro fabrica) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.fabrica = Objects.requireNonNull(fabrica, "fabrica");
    }

    /** Envia a consulta e devolve um objeto JSON. */
    public JsonNode objeto(String caminho) {
        JsonNode no = ler(caminho);
        if (!no.isObject()) {
            throw formatoInesperado(caminho, "objeto JSON");
        }
        return no;
    }

    /** Envia a consulta e devolve os elementos de um array JSON. */
    public List<JsonNode> lista(String caminho) {
        JsonNode no = ler(caminho);
        if (!no.isArray()) {
            throw formatoInesperado(caminho, "array JSON");
        }
        List<JsonNode> elementos = new ArrayList<>();
        no.forEach(elementos::add);
        return List.copyOf(elementos);
    }

    /** Envia a consulta ao portal, converte erros na exceção da API e devolve o corpo lido. */
    public JsonNode ler(String caminho) {
        return executar(RequisicaoSiscomex.get(caminho)).corpo();
    }

    /**
     * Envia um POST com o corpo serializado em JSON e devolve o JSON da resposta. Campos nulos do corpo são
     * omitidos, em vez de enviados como {@code null}.
     */
    public JsonNode enviar(String caminho, Object corpo) {
        return executar(RequisicaoSiscomex.post(caminho, serializar(caminho, corpo))).corpo();
    }

    // ---------------------------------------------------------------- respostas tipadas

    /**
     * Envia a consulta e converte o objeto JSON da resposta em {@code tipo}. Campos da resposta que não existem
     * no tipo são ignorados. Um componente {@code @JacksonInject(PortalClient.RAW) JsonNode} recebe a resposta
     * completa.
     */
    public <T> T objeto(String caminho, Class<T> tipo) {
        Resposta resposta = executar(RequisicaoSiscomex.get(caminho));
        if (!resposta.corpo().isObject()) {
            throw formatoInesperado(caminho, "objeto JSON");
        }
        return converter(resposta.corpo(), tipo, caminho, resposta.status());
    }

    /** Envia a consulta e converte cada elemento do array JSON em {@code tipo} (o {@code raw} é o elemento). */
    public <T> List<T> lista(String caminho, Class<T> tipo) {
        Resposta resposta = executar(RequisicaoSiscomex.get(caminho));
        if (!resposta.corpo().isArray()) {
            throw formatoInesperado(caminho, "array JSON");
        }
        List<T> elementos = new ArrayList<>();
        for (JsonNode elemento : resposta.corpo()) {
            elementos.add(converter(elemento, tipo, caminho, resposta.status()));
        }
        return List.copyOf(elementos);
    }

    /** Envia um POST (como {@link #enviar(String, Object)}) e converte a resposta em {@code tipo}. */
    public <T> T enviar(String caminho, Object corpo, Class<T> tipo) {
        Resposta resposta = executar(RequisicaoSiscomex.post(caminho, serializar(caminho, corpo)));
        return converter(resposta.corpo(), tipo, caminho, resposta.status());
    }

    private <T> T converter(JsonNode no, Class<T> tipo, String caminho, int status) {
        try {
            return JSON.readerFor(tipo).with(new InjectableValues.Std().addValue(RAW, no)).readValue(no);
        } catch (JacksonException e) {
            throw fabrica.criar("Resposta do portal para " + ClienteApi.paraLog(caminho) + " não pôde ser lida como "
                    + tipo.getSimpleName() + ".", status, null, List.of(), e);
        }
    }

    // ---------------------------------------------------------------- transporte

    private record Resposta(JsonNode corpo, int status) {
    }

    private String serializar(String caminho, Object corpo) {
        Objects.requireNonNull(corpo, "corpo");
        try {
            return JSON.writeValueAsString(corpo);
        } catch (JacksonException e) {
            throw fabrica.criar("Não foi possível serializar o corpo da requisição para " + ClienteApi.paraLog(caminho) + ".",
                    0, null, List.of(), e);
        }
    }

    private Resposta executar(RequisicaoSiscomex requisicao) {
        String caminho = requisicao.caminho();
        RespostaSiscomex resposta = gateway.send(requisicao);
        if (!resposta.ehSucesso() && resposta.status() != MULTI_STATUS) {
            throw erro(resposta, caminho);
        }
        String corpo = resposta.corpo();
        if (corpo == null || corpo.isBlank()) {
            throw fabrica.criar("O portal respondeu HTTP " + resposta.status() + " sem corpo para " + ClienteApi.paraLog(caminho) + ".",
                    resposta.status(), null, List.of(), null);
        }
        try {
            return new Resposta(JSON.readTree(corpo), resposta.status());
        } catch (JacksonException e) {
            throw fabrica.criar("Resposta do portal para " + ClienteApi.paraLog(caminho) + " não é JSON válido.",
                    resposta.status(), null, List.of(), e);
        }
    }

    private PortalException erro(RespostaSiscomex resposta, String caminho) {
        String codigo = null;
        List<ErroNegocio> erros = new ArrayList<>();
        String mensagem = null;
        try {
            JsonNode corpo = JSON.readTree(resposta.corpo() == null ? "" : resposta.corpo());
            codigo = texto(corpo, "internal-code");
            mensagem = texto(corpo, "message");
            for (JsonNode e : corpo.path("errors")) {
                erros.add(new ErroNegocio(texto(e, "code"), texto(e, "field"), texto(e, "message")));
            }
        } catch (JacksonException | NullPointerException ignorado) {
            // Corpo de erro que não é JSON: segue apenas com o status.
        }
        String descricao = mensagem != null ? mensagem : "Falha na consulta ao Portal Único";
        return fabrica.criar(descricao + " (HTTP " + resposta.status() + " em " + ClienteApi.paraLog(caminho) + ")",
                resposta.status(), codigo, erros, null);
    }

    private PortalException formatoInesperado(String caminho, String esperado) {
        return fabrica.criar("Resposta do portal para " + ClienteApi.paraLog(caminho) + " não tem o formato esperado (" + esperado + ").",
                200, null, List.of(), null);
    }

    private static String texto(JsonNode no, String campo) {
        JsonNode valor = no.path(campo);
        return valor.isMissingNode() || valor.isNull() ? null : valor.asString();
    }
}
