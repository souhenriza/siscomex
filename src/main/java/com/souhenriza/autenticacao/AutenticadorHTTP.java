package com.souhenriza.autenticacao;

import com.souhenriza.Ambiente;
import com.souhenriza.autenticacao.credencial.Credenciais;
import com.souhenriza.autenticacao.dto.ErroRespostaAutenticacao;
import com.souhenriza.autenticacao.erro.CodigoErroPortal;
import com.souhenriza.autenticacao.erro.ExcecaoSiscomex;
import com.souhenriza.autenticacao.erro.FalhasPortal;
import com.souhenriza.autenticacao.sessao.Tokens;
import com.souhenriza.http.PortalHttp;
import com.souhenriza.http.PortalHttpClients;
import com.souhenriza.http.PortalRequest;
import com.souhenriza.http.PortalResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Obtém os tokens de sessão do portal com as credenciais informadas. O endpoint depende do tipo de credencial:
 * {@code POST /portal/api/autenticar} para certificado digital e {@code /portal/api/autenticar/chave-acesso} para
 * chave de acesso de usuário.
 * <p>
 * A comunicação é feita pelo {@link PortalHttp}. Esta classe cuida só do que é próprio da autenticação: o cabeçalho
 * {@code Role-Type}, a janela de parada programada, a leitura dos tokens dos cabeçalhos da resposta e a tradução
 * dos erros do portal.
 */
public class AutenticadorHTTP implements Autenticador {

    private static final Logger LOG = LoggerFactory.getLogger(AutenticadorHTTP.class);

    private static final String CABECALHO_PERFIL = "Role-Type";
    private static final String CABECALHO_JWT = "Set-Token";
    private static final String CABECALHO_CSRF = "X-CSRF-Token";

    /** Intervalo mínimo entre autenticações válidas, conforme PLAT-ER2033. */
    private static final Duration INTERVALO_LIMITE_TAXA = Duration.ofSeconds(60);

    /** Tempo limite padrão da autenticação. */
    public static final Duration TIMEOUT_PADRAO = Duration.ofSeconds(30);

    private static final ObjectMapper LEITOR_JSON = new ObjectMapper();

    private final Credenciais credenciais;
    private final PortalHttp portal;
    private final Clock relogio;

    /**
     * Autenticador para um ambiente, com as credenciais e o tempo limite padrão, usando um cliente próprio.
     *
     * @param ambiente    ambiente do portal (produção, validação ou homologação)
     * @param credenciais credenciais: certificado e perfil
     * @throws NullPointerException if {@code ambiente} ou {@code credenciais} for nulo
     * @throws ExcecaoSiscomex      se a URL do ambiente não usar HTTPS
     */
    public AutenticadorHTTP(Ambiente ambiente, Credenciais credenciais) {
        this(Objects.requireNonNull(ambiente, "ambiente"), credenciais, criarCliente(credenciais));
    }

    /**
     * Autenticador para um ambiente, com um cliente HTTP informado (útil para compartilhar o pool de conexões).
     *
     * @param ambiente    ambiente do portal
     * @param credenciais credenciais: certificado e perfil
     * @param http        cliente HTTP a usar na autenticação
     * @throws NullPointerException if algum argumento for nulo
     * @throws ExcecaoSiscomex      se a URL do ambiente não usar HTTPS
     */
    public AutenticadorHTTP(Ambiente ambiente, Credenciais credenciais, HttpClient http) {
        this(Objects.requireNonNull(ambiente, "ambiente").getUrl(), credenciais, http, TIMEOUT_PADRAO);
    }

    /**
     * Autenticador para uma URL base, com um tempo limite, usando um cliente próprio.
     *
     * @param urlBase     URL base do portal, somente HTTPS
     * @param credenciais credenciais: certificado e perfil
     * @param timeout     tempo limite da autenticação, positivo
     * @throws NullPointerException     if algum argumento for nulo
     * @throws IllegalArgumentException se {@code timeout} não for positivo
     * @throws ExcecaoSiscomex          se {@code urlBase} não usar HTTPS
     */
    public AutenticadorHTTP(String urlBase, Credenciais credenciais, Duration timeout) {
        this(urlBase, credenciais, criarCliente(credenciais), timeout);
    }

    /**
     * Autenticador para uma URL base, com cliente e tempo limite informados.
     *
     * @param urlBase     URL base do portal, somente HTTPS
     * @param credenciais credenciais: certificado e perfil
     * @param http        cliente HTTP a usar na autenticação
     * @param timeout     tempo limite da autenticação, positivo
     * @throws NullPointerException     if algum argumento for nulo
     * @throws IllegalArgumentException se {@code timeout} não for positivo
     * @throws ExcecaoSiscomex          se {@code urlBase} não usar HTTPS
     */
    public AutenticadorHTTP(String urlBase, Credenciais credenciais, HttpClient http, Duration timeout) {
        this(urlBase, credenciais, http, timeout, Clock.systemUTC());
    }

    /**
     * Autenticador completo, com relógio injetável para os testes da janela de parada.
     *
     * @param urlBase     URL base do portal, somente HTTPS
     * @param credenciais credenciais: certificado e perfil
     * @param http        cliente HTTP a usar na autenticação
     * @param timeout     tempo limite da autenticação, positivo
     * @param relogio     relógio usado para verificar a janela de parada
     * @throws NullPointerException     if algum argumento for nulo
     * @throws IllegalArgumentException se {@code timeout} não for positivo
     * @throws ExcecaoSiscomex          se {@code urlBase} não usar HTTPS
     */
    public AutenticadorHTTP(String urlBase, Credenciais credenciais, HttpClient http, Duration timeout,
                            Clock relogio) {
        this.credenciais = Objects.requireNonNull(credenciais, "credenciais");
        this.portal = new PortalHttp(Objects.requireNonNull(urlBase, "urlBase"),
                Objects.requireNonNull(http, "http"), Objects.requireNonNull(timeout, "timeout"));
        this.relogio = Objects.requireNonNull(relogio, "relogio");
    }

    /**
     * Cliente HTTP para as credenciais informadas, com o {@code SSLContext} do certificado.
     *
     * @param credenciais credenciais; o contexto TLS pode ser nulo
     * @return cliente novo (tipo não genérico)
     * @throws NullPointerException se {@code credenciais} for nulo
     */
    private static HttpClient criarCliente(Credenciais credenciais) {
        Objects.requireNonNull(credenciais, "credenciais");
        return PortalHttpClients.create(credenciais.contextoSsl());
    }

    /**
     * Envia a autenticação e devolve os tokens dos cabeçalhos da resposta.
     *
     * @return JWT ({@code Set-Token}) e CSRF ({@code X-CSRF-Token}) da nova sessão (tipo não genérico)
     * @throws ExcecaoSiscomex se a parada programada estiver em curso (código {@code PARADA_PROGRAMADA}), se a
     *                         comunicação falhar, se o portal recusar a autenticação (com o código de erro quando
     *                         conhecido), ou se a resposta não trouxer os dois tokens
     */
    @Override
    public Tokens autenticar() throws ExcecaoSiscomex {
        Optional<Duration> manutencao = JanelaDeManutencao.tempoRestante(relogio.instant());
        if (manutencao.isPresent()) {
            LOG.info("Autenticação não enviada: o portal está em parada programada.");
            throw FalhasPortal.erroDoPortal(CodigoErroPortal.PARADA_PROGRAMADA, null, manutencao.get());
        }

        PortalRequest requisicao = PortalRequest.post(credenciais.caminhoAutenticacao())
                .header(CABECALHO_PERFIL, credenciais.perfil().name())
                .headers(credenciais.cabecalhosAutenticacao());

        PortalResponse resposta;
        try {
            resposta = portal.send(requisicao);
        } catch (ExcecaoSiscomex e) {
            LOG.warn("Falha de comunicação durante a autenticação: {}", e.getMessage());
            throw e;
        }

        if (!resposta.isSuccess()) {
            throw falhaHttp(resposta.status(), resposta.body());
        }

        String jwt = resposta.header(CABECALHO_JWT).orElse(null);
        if (jwt == null || jwt.isBlank()) {
            throw new ExcecaoSiscomex("A resposta da autenticação não retornou o token JWT. ");
        }

        String tokenCsrf = resposta.header(CABECALHO_CSRF).orElse(null);
        if (tokenCsrf == null || tokenCsrf.isBlank()) {
            throw new ExcecaoSiscomex("A resposta da autenticação não retornou o token CSRF. ");
        }

        LOG.debug("Autenticação concluída no portal.");
        return new Tokens(jwt, tokenCsrf);
    }

    /**
     * Converte uma resposta de erro da autenticação na exceção adequada.
     *
     * @param status código HTTP recusado
     * @param corpo  corpo da resposta, possivelmente vazio ou não JSON
     * @return exceção com o código do portal quando conhecido, ou genérica (tipo não genérico)
     */
    private ExcecaoSiscomex falhaHttp(int status, String corpo) {
        ErroRespostaAutenticacao erro = lerErro(corpo);

        if (erro == null) {
            LOG.warn("Autenticação recusada pelo portal (HTTP {}) sem detalhes.", status);
            return new ExcecaoSiscomex("Falha na autenticação no portal (HTTP " + status
                    + ") sem detalhes do erro.", null, status >= 500);
        }

        Optional<CodigoErroPortal> codigo = CodigoErroPortal.deCodigo(erro.codigo());

        if (codigo.isEmpty()) {
            LOG.warn("Autenticação recusada pelo portal com código não mapeado: {}", erro.codigo());
            return new ExcecaoSiscomex("Erro na autenticação no portal (código " + erro.codigo()
                    + "). Mensagem do portal: " + erro.mensagem(), erro);
        }

        LOG.warn("Autenticação recusada pelo portal: {}", codigo.get().codigo());
        return FalhasPortal.erroDoPortal(codigo.get(), erro, tempoEsperaPara(codigo.get()));
    }

    /**
     * Tempo a aguardar antes de tentar de novo, conforme o código de erro.
     *
     * @param codigo código de erro do portal
     * @return intervalo de espera, ou {@code null} quando o código não define espera (tipo não genérico)
     */
    private Duration tempoEsperaPara(CodigoErroPortal codigo) {
        return switch (codigo) {
            case LIMITE_AUTENTICACAO -> INTERVALO_LIMITE_TAXA;
            case PARADA_PROGRAMADA -> JanelaDeManutencao.tempoRestante(relogio.instant()).orElse(null);
            default -> null;
        };
    }

    /**
     * Lê o corpo de erro no formato {@link ErroRespostaAutenticacao}. Corpos vazios ou que não são JSON (por exemplo,
     * páginas de erro de proxy) resultam em {@code null}.
     *
     * @param corpo corpo da resposta de erro, pode ser nulo
     * @return erro lido, ou {@code null} se o corpo estiver vazio ou não for JSON válido (tipo não genérico)
     */
    private static ErroRespostaAutenticacao lerErro(String corpo) {
        if (corpo == null || corpo.isBlank()) {
            return null;
        }

        try {
            return LEITOR_JSON.readValue(corpo, ErroRespostaAutenticacao.class);
        } catch (JacksonException e) {
            return null;
        }
    }
}
