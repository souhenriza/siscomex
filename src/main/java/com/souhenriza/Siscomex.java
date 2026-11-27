package com.souhenriza;

import com.souhenriza.autenticacao.api.ClienteApi;
import com.souhenriza.autenticacao.api.RequisicaoSiscomex;
import com.souhenriza.autenticacao.api.RespostaSiscomex;
import com.souhenriza.autenticacao.Autenticador;
import com.souhenriza.autenticacao.AutenticadorHTTP;
import com.souhenriza.autenticacao.credencial.Credenciais;
import com.souhenriza.autenticacao.sessao.GerenciadorSessao;
import com.souhenriza.http.PortalHttpClients;

import java.net.http.HttpClient;
import java.time.Clock;
import java.util.Objects;

/**
 * Fachada da biblioteca: reúne ambiente, credenciais, gerenciamento de sessão e envio de requisições.
 * <p>
 * A autenticação e os serviços usam um único {@link HttpClient}, criado a partir do certificado das
 * credenciais, para compartilhar o pool de conexões e a configuração TLS.
 */
public class Siscomex {

    private final Ambiente ambiente;
    private final Credenciais credential;
    private final GerenciadorSessao sessionManagement;
    private final ClienteApi api;

    /**
     * Cria a fachada para um ambiente e credenciais, com um cliente HTTP compartilhado.
     *
     * @param ambiente ambiente do portal
     * @param credentials credenciais: certificado e perfil
     * @throws NullPointerException if algum argumento for nulo
     * @throws com.souhenriza.autenticacao.erro.ExcecaoSiscomex se a URL do ambiente não usar HTTPS
     */
    public Siscomex(Ambiente ambiente, Credenciais credentials) {
        this(ambiente, credentials, PortalHttpClients.create(
                Objects.requireNonNull(credentials, "credenciais").contextoSsl()));
    }

    /**
     * Cria a fachada com um autenticador próprio (usado nos testes, sem rede).
     *
     * @param ambiente   ambiente do portal
     * @param credentials   credenciais
     * @param authenticator autenticador que fornece os tokens
     * @throws NullPointerException if algum argumento for nulo
     */
    Siscomex(Ambiente ambiente, Credenciais credentials, Autenticador authenticator) {
        this(ambiente, credentials, new GerenciadorSessao(Objects.requireNonNull(authenticator, "autenticador")),
                PortalHttpClients.create(Objects.requireNonNull(credentials, "credenciais").contextoSsl()),
                Clock.systemUTC());
    }

    /**
     * Cria a fachada com um cliente HTTP informado; autenticação e serviços compartilham esse cliente.
     *
     * @param ambiente ambiente do portal
     * @param credentials credenciais
     * @param http        cliente HTTP compartilhado
     * @throws NullPointerException if algum argumento for nulo
     */
    private Siscomex(Ambiente ambiente, Credenciais credentials, HttpClient http) {
        this(ambiente, credentials,
                new GerenciadorSessao(new AutenticadorHTTP(Objects.requireNonNull(ambiente, "ambiente").getUrl(),
                        Objects.requireNonNull(credentials, "credenciais"), http, AutenticadorHTTP.TIMEOUT_PADRAO)),
                http, Clock.systemUTC());
    }

    /**
     * Construtor completo, com gerenciador de sessão, cliente e relógio informados.
     *
     * @param ambiente ambiente do portal
     * @param credentials credenciais
     * @param sessions    gerenciador de sessão
     * @param http        cliente HTTP dos serviços
     * @param clock       relógio usado pelos serviços
     * @throws NullPointerException if algum argumento for nulo
     * @throws com.souhenriza.autenticacao.erro.ExcecaoSiscomex se a URL do ambiente não usar HTTPS
     */
    Siscomex(Ambiente ambiente, Credenciais credentials, GerenciadorSessao sessions, HttpClient http, Clock clock) {
        this.ambiente = Objects.requireNonNull(ambiente, "ambiente");
        this.credential = Objects.requireNonNull(credentials, "credenciais");
        this.sessionManagement = Objects.requireNonNull(sessions, "sessoes");
        this.api = new ClienteApi(ambiente.getUrl(), sessions, http, clock);
    }

    /**
     * Montador da fachada, para quem prefere configurar por etapas.
     */
    public static class Builder {
        private Ambiente ambiente;
        private Credenciais credentials;

        /**
         * Define o ambiente.
         *
         * @param ambiente ambiente do portal
         * @return este builder (tipo não genérico)
         */
        public Builder environment(Ambiente ambiente) {
            this.ambiente = ambiente;
            return this;
        }

        /**
         * Define as credenciais.
         *
         * @param credentials credenciais: certificado e perfil
         * @return este builder (tipo não genérico)
         */
        public Builder credentials(Credenciais credentials) {
            this.credentials = credentials;
            return this;
        }

        /**
         * Cria a fachada com os valores configurados.
         *
         * @return fachada nova (tipo não genérico)
         * @throws NullPointerException if o ambiente ou as credenciais não tiverem sido definidos
         */
        public Siscomex build() {
            return new Siscomex(ambiente, credentials);
        }
    }

    /**
     * Ambiente configurado.
     *
     * @return ambiente do portal (tipo não genérico)
     */
    public Ambiente getEnvironment() {
        return ambiente;
    }

    /**
     * Credenciais configuradas.
     *
     * @return credenciais (tipo não genérico)
     */
    public Credenciais getCredential() {
        return credential;
    }

    /**
     * Gerenciador de sessão: obtém, renova e reaproveita os tokens usados pelos serviços.
     *
     * @return gerenciador de sessão (tipo não genérico)
     */
    public GerenciadorSessao getSessionManagement() {
        return sessionManagement;
    }

    /**
     * Envia uma requisição autenticada a um serviço do portal. Os tokens de sessão são obtidos, renovados a
     * cada resposta e refeitos uma vez quando o portal responde 401.
     *
     * @param request requisição do domínio
     * @return resposta do portal, qualquer que seja o status exceto 401 (tipo não genérico)
     * @throws NullPointerException if {@code request} for nulo
     * @throws com.souhenriza.autenticacao.erro.ExcecaoSiscomex em parada programada, falha de comunicação, falha de
     *                                                     autenticação ou sessão rejeitada após nova autenticação
     * @see ClienteApi#send(RequisicaoSiscomex)
     */
    public RespostaSiscomex send(RequisicaoSiscomex request) {
        return api.send(request);
    }
}
