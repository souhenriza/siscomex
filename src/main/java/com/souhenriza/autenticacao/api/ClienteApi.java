package com.souhenriza.autenticacao.api;

import com.souhenriza.autenticacao.JanelaDeManutencao;
import com.souhenriza.autenticacao.erro.CodigoErroPortal;
import com.souhenriza.autenticacao.erro.ExcecaoSiscomex;
import com.souhenriza.autenticacao.erro.FalhasPortal;
import com.souhenriza.autenticacao.sessao.GerenciadorSessao;
import com.souhenriza.autenticacao.sessao.Sessao;
import com.souhenriza.http.HttpMethod;
import com.souhenriza.http.PortalHttp;
import com.souhenriza.http.PortalRequest;
import com.souhenriza.http.PortalResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Envia requisições autenticadas aos serviços do portal.
 * <p>
 * Para cada chamada, acrescenta {@code Authorization} (JWT) e {@code X-CSRF-Token} à requisição, atualiza o CSRF com
 * o valor devolvido em cada resposta e, se o portal responder 401, autentica de novo e repete a chamada uma vez. A
 * montagem e o envio HTTP ficam no {@link PortalHttp}. Respostas com outros códigos (incluindo 403, 404, 422 e 500)
 * são devolvidas ao chamador, porque a interpretação dos erros de negócio depende de cada serviço.
 */
public class ClienteApi {

    private static final Logger LOG = LoggerFactory.getLogger(ClienteApi.class);

    private static final int NAO_AUTORIZADO = 401;
    private static final String CABECALHO_AUTORIZACAO = "Authorization";
    private static final String CABECALHO_CSRF = "X-CSRF-Token";
    private static final Pattern IDENTIFICADOR_IMPORTADOR = Pattern.compile("(/chaves-acesso/importadores/)[^/?]+");
    private static final Duration TIMEOUT_SERVICO = Duration.ofSeconds(60);

    private final PortalHttp portal;
    private final GerenciadorSessao sessoes;
    private final Clock relogio;

    /**
     * Cria o cliente dos serviços.
     *
     * @param urlBase  URL base do portal, somente HTTPS
     * @param sessoes  gerenciador de sessão que fornece e renova os tokens
     * @param http     cliente HTTP a usar nas chamadas
     * @param relogio  relógio usado para verificar a janela de parada
     * @throws NullPointerException if algum argumento for nulo
     * @throws ExcecaoSiscomex      se {@code urlBase} não usar HTTPS
     */
    public ClienteApi(String urlBase, GerenciadorSessao sessoes, HttpClient http, Clock relogio) {
        this.portal = new PortalHttp(Objects.requireNonNull(urlBase, "urlBase"),
                Objects.requireNonNull(http, "http"), TIMEOUT_SERVICO);
        this.sessoes = Objects.requireNonNull(sessoes, "sessoes");
        this.relogio = Objects.requireNonNull(relogio, "relogio");
    }

    /**
     * Envia a requisição ao portal, com a sessão vigente. Se a sessão for rejeitada (401), autentica de novo e
     * repete a chamada uma vez.
     *
     * @param requisicao requisição do domínio, com método, caminho e corpo opcional
     * @return resposta do portal, qualquer que seja o status exceto 401 (tipo não genérico)
     * @throws NullPointerException if {@code requisicao} for nula
     * @throws ExcecaoSiscomex      se o portal estiver em parada programada (código {@code PARADA_PROGRAMADA}),
     *                              se a comunicação falhar, se a autenticação falhar, ou se o portal continuar
     *                              respondendo 401 mesmo após nova autenticação
     */
    public RespostaSiscomex send(RequisicaoSiscomex requisicao) {
        Objects.requireNonNull(requisicao, "requisicao");

        Optional<Duration> manutencao = JanelaDeManutencao.tempoRestante(relogio.instant());
        if (manutencao.isPresent()) {
            LOG.info("Requisição {} {} não enviada: o portal está em parada programada.", requisicao.metodo(), paraLog(requisicao.caminho()));
            throw FalhasPortal.erroDoPortal(CodigoErroPortal.PARADA_PROGRAMADA, null, manutencao.get());
        }

        RespostaSiscomex resposta = sessoes.executar(
                sessao -> chamar(requisicao, sessao),
                ClienteApi::sessaoRejeitadaERegistrada);

        if (resposta.status() == NAO_AUTORIZADO) {
            LOG.warn("O portal rejeitou a sessão de {} {} mesmo após nova autenticação.", requisicao.metodo(), paraLog(requisicao.caminho()));
            throw new ExcecaoSiscomex("O portal rejeitou a sessão mesmo após nova autenticação (HTTP 401).");
        }

        return resposta;
    }

    /**
     * Uma tentativa de chamada com uma sessão específica. Atualiza o CSRF com o valor da resposta, exceto quando a
     * resposta é 401 (a sessão será descartada de qualquer forma).
     *
     * @param requisicao requisição do domínio
     * @param sessao      sessão cujos tokens autenticam a chamada
     * @return resposta convertida no tipo do domínio (tipo não genérico)
     * @throws ExcecaoSiscomex se a comunicação falhar (com a causa original)
     */
    private RespostaSiscomex chamar(RequisicaoSiscomex requisicao, Sessao sessao) {
        PortalRequest requisicaoPortal = paraRequisicaoPortal(requisicao)
                .header(CABECALHO_AUTORIZACAO, sessao.tokenAutenticacao())
                .header(CABECALHO_CSRF, sessao.tokenCsrf());

        PortalResponse bruta;
        try {
            bruta = portal.send(requisicaoPortal);
        } catch (ExcecaoSiscomex e) {
            LOG.warn("Falha de comunicação em {} {}: {}", requisicao.metodo(), paraLog(requisicao.caminho()), e.getMessage());
            throw e;
        }

        RespostaSiscomex resposta = new RespostaSiscomex(bruta.status(), bruta.body(), bruta.firstValues());

        if (resposta.status() != NAO_AUTORIZADO) {
            sessoes.atualizarAPartirDaResposta(null, resposta.cabecalho(CABECALHO_CSRF).orElse(null));
        }

        LOG.debug("{} {} respondeu HTTP {}", requisicao.metodo(), paraLog(requisicao.caminho()), resposta.status());
        return resposta;
    }

    /**
     * Converte a requisição do domínio na requisição HTTP, sem os headers de sessão.
     *
     * @param requisicao requisição do domínio
     * @return requisição HTTP com método, caminho e, se houver, corpo JSON (tipo não genérico)
     * @throws IllegalArgumentException se o método não for {@code GET}, {@code POST}, {@code PUT} ou {@code DELETE}
     */
    private static PortalRequest paraRequisicaoPortal(RequisicaoSiscomex requisicao) {
        PortalRequest requisicaoPortal = PortalRequest.of(HttpMethod.valueOf(requisicao.metodo()), requisicao.caminho());
        return requisicao.corpo() == null ? requisicaoPortal : requisicaoPortal.json(requisicao.corpo());
    }

    /**
     * Versão do caminho segura para log: o número de identificação do importador (CPF ou CNPJ), que aparece em
     * {@code /chaves-acesso/importadores/{ni}}, é substituído por {@code ***}.
     *
     * @param caminho caminho do serviço
     * @return caminho com o NI mascarado (tipo não genérico)
     */
    public static String ocultarIdentificador(String caminho) {
        return IDENTIFICADOR_IMPORTADOR.matcher(caminho).replaceAll("$1***");
    }

    /**
     * Versão do caminho para log e mensagens de erro: remove a query string (que pode conter chave de NF-e, CNPJ de
     * beneficiário ou número de DUIMP) e mascara o NI do importador.
     *
     * @param caminho caminho do serviço, possivelmente com query string
     * @return caminho sem query e com o NI mascarado (tipo não genérico)
     */
    public static String paraLog(String caminho) {
        int query = caminho.indexOf('?');
        return ocultarIdentificador(query >= 0 ? caminho.substring(0, query) : caminho);
    }

    /**
     * Predicado do {@link GerenciadorSessao#executar}: indica se a resposta rejeitou a sessão e registra o fato.
     *
     * @param resposta resposta da tentativa
     * @return {@code true} se a resposta for 401, o que faz a chamada ser repetida (tipo não genérico)
     */
    private static boolean sessaoRejeitadaERegistrada(RespostaSiscomex resposta) {
        if (resposta.status() == NAO_AUTORIZADO) {
            LOG.info("Sessão rejeitada pelo portal (HTTP 401). Renovando a autenticação e repetindo a chamada.");
            return true;
        }
        return false;
    }
}
