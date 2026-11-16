package com.souhenriza;

import com.souhenriza.api.ApiClient;
import com.souhenriza.api.SiscomexRequest;
import com.souhenriza.api.SiscomexResponse;
import com.souhenriza.auth.Authenticator;
import com.souhenriza.auth.AuthenticatorHTTP;
import com.souhenriza.auth.credential.Credentials;
import com.souhenriza.session.SessionManagement;

import java.net.http.HttpClient;
import java.time.Clock;
import java.util.Objects;

public class Siscomex {

    private final Environment ambiente;
    private final Credentials credential;
    private final SessionManagement sessionManagement;
    private final ApiClient api;

    public Siscomex(Environment ambiente, Credentials credentials) {
        this(ambiente, credentials, new AuthenticatorHTTP(ambiente, credentials));
    }

    Siscomex(Environment ambiente, Credentials credentials, Authenticator authenticator) {
        this(ambiente, credentials, new SessionManagement(Objects.requireNonNull(authenticator, "autenticador")),
                ApiClient.defaultHttpClient(Objects.requireNonNull(credentials, "credenciais").sslContext()),
                Clock.systemUTC());
    }

    Siscomex(Environment ambiente, Credentials credentials, SessionManagement sessions, HttpClient http, Clock clock) {
        this.ambiente = Objects.requireNonNull(ambiente, "ambiente");
        this.credential = Objects.requireNonNull(credentials, "credenciais");
        this.sessionManagement = Objects.requireNonNull(sessions, "sessoes");
        this.api = new ApiClient(ambiente.getUrl(), sessions, http, clock);
    }


    public static class Builder {
        private Environment environment;
        private Credentials credentials;


        public Builder environment(Environment environment) {
            this.environment = environment;
            return this;
        }

        public Builder credentials(Credentials credentials) {
            this.credentials = credentials;
            return this;
        }

        public Siscomex build() {
            return new Siscomex(environment, credentials);
        }
    }

    public Environment getAmbiente() {
        return ambiente;
    }

    public Credentials getCredential() {
        return credential;
    }

    /** Gerenciador de sessão: obtém, renova e reaproveita os tokens usados pelos serviços. */
    public SessionManagement getSessionManagement() {
        return sessionManagement;
    }

    /**
     * Envia uma requisição autenticada a um serviço do portal. Os tokens de sessão são obtidos,
     * renovados a cada resposta e refeitos uma vez quando o portal responde 401.
     *
     * @see ApiClient#send(SiscomexRequest)
     */
    public SiscomexResponse send(SiscomexRequest request) {
        return api.send(request);
    }

}
