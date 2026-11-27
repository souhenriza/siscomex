package com.souhenriza.http;

import com.souhenriza.autenticacao.UrlPortal;
import com.souhenriza.autenticacao.erro.FalhasPortal;
import com.souhenriza.autenticacao.erro.ExcecaoSiscomex;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Transporte HTTP único para o Portal Único: monta a requisição, envia e devolve a resposta.
 * <p>
 * É a única classe que monta um {@link HttpRequest} e a única que chama o {@link HttpClient}. Ela define os
 * cabeçalhos padrão ({@code Accept: application/json} e, quando há corpo, {@code Content-Type: application/json})
 * e aplica o tempo limite de cada requisição. Não interpreta o status nem o corpo: 401, 404, 422 e 500 voltam
 * como {@link PortalResponse}, porque o significado de cada erro depende do serviço.
 * <p>
 * A base URL é validada como HTTPS a cada construção ({@link UrlPortal#exigirHttps(String)}), e a instância é
 * imutável e segura para uso concorrente, desde que o {@link HttpClient} também seja.
 */
public final class PortalHttp {

    /** Cabeçalho {@code Accept}. */
    public static final String ACCEPT = "Accept";

    /** Cabeçalho {@code Content-Type}. */
    public static final String CONTENT_TYPE = "Content-Type";

    /** Valor JSON usado nos cabeçalhos padrão. */
    public static final String APPLICATION_JSON = "application/json";

    private final String baseUrl;
    private final HttpClient client;
    private final Duration timeout;

    /**
     * Cria o transporte para uma base URL e um cliente.
     *
     * @param baseUrl base URL do portal ou do serviço, somente HTTPS; a barra final é ignorada
     * @param client  cliente HTTP a usar nas chamadas
     * @param timeout tempo máximo de cada requisição, positivo
     * @throws NullPointerException     se algum argumento for nulo
     * @throws IllegalArgumentException se {@code timeout} não for positivo
     * @throws ExcecaoSiscomex        se {@code baseUrl} não usar HTTPS, for malformada ou não tiver host
     */
    public PortalHttp(String baseUrl, HttpClient client, Duration timeout) {
        this.baseUrl = UrlPortal.exigirHttps(Objects.requireNonNull(baseUrl, "baseUrl"));
        this.client = Objects.requireNonNull(client, "cliente http");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("O timeout deve ser positivo.");
        }
    }

    /**
     * Envia a requisição e devolve a resposta, qualquer que seja o status.
     *
     * @param request requisição já montada
     * @return resposta recebida do portal, sem interpretação do status (tipo não genérico)
     * @throws NullPointerException if {@code request} for nulo
     * @throws ExcecaoSiscomex    se a comunicação falhar: TLS, conexão ou tempo esgotado (com a causa original).
     *                              A exceção é retentável quando a falha é de conexão ou de tempo.
     * @throws ExcecaoSiscomex    se a thread for interrompida durante a espera; o estado de interrupção é restaurado
     */
    public PortalResponse send(PortalRequest request) {
        Objects.requireNonNull(request, "requisicao");
        HttpResponse<String> raw;
        try {
            raw = client.send(toHttpRequest(request), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw FalhasPortal.falhaDeComunicacao(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExcecaoSiscomex("Interrompido durante a comunicação com o portal.", e);
        }
        return toPortalResponse(raw);
    }

    /**
     * Converte a requisição do domínio no {@link HttpRequest} do JDK, com os cabeçalhos padrão e o tempo limite.
     * A ordem importa: os padrões são definidos antes dos cabeçalhos do chamador, que podem substituí-los.
     *
     * @param request requisição do domínio
     * @return requisição pronta para envio (tipo não genérico)
     * @throws NullPointerException     se {@code request} for nulo
     * @throws IllegalArgumentException se a URL montada não for válida
     */
    HttpRequest toHttpRequest(PortalRequest request) {
        Objects.requireNonNull(request, "requisicao");
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + request.target()))
                .timeout(timeout)
                .setHeader(ACCEPT, APPLICATION_JSON);

        if (request.body().isPresent()) {
            builder.setHeader(CONTENT_TYPE, APPLICATION_JSON);
        }
        for (Map.Entry<String, String> header : request.headers().entrySet()) {
            builder.setHeader(header.getKey(), header.getValue());
        }

        HttpRequest.BodyPublisher publisher = request.body()
                .map(HttpRequest.BodyPublishers::ofString)
                .orElseGet(HttpRequest.BodyPublishers::noBody);
        return builder.method(request.method().name(), publisher).build();
    }

    /**
     * Copia a resposta do JDK para o tipo do domínio, preservando os valores repetidos de cada header.
     *
     * @param raw resposta do {@link HttpClient}
     * @return resposta do domínio (tipo não genérico)
     */
    private static PortalResponse toPortalResponse(HttpResponse<String> raw) {
        Map<String, List<String>> headers = raw.headers().map();
        return new PortalResponse(raw.statusCode(), raw.body(), headers);
    }
}
