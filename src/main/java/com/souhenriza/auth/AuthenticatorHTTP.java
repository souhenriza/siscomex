package com.souhenriza.auth;

import com.souhenriza.Environment;
import com.souhenriza.auth.credential.Credentials;
import com.souhenriza.auth.dto.AuthResponseError;
import com.souhenriza.error.PortalErrorCode;
import com.souhenriza.error.PortalFailures;
import com.souhenriza.error.SiscomexException;
import com.souhenriza.session.Tokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public class AuthenticatorHTTP implements Authenticator {

    private static final Logger LOG = LoggerFactory.getLogger(AuthenticatorHTTP.class);

    private static final String AUTH_PATH = "/portal/api/autenticar";

    /** Intervalo mínimo entre autenticações válidas, conforme PLAT-ER2033. */
    private static final Duration RATE_LIMIT_INTERVAL = Duration.ofSeconds(60);

    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration TIMEOUT_PATTERN = Duration.ofSeconds(30);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final URI uri;
    private final Credentials credentials;
    private final HttpClient client;
    private final Duration timeout;
    private final Clock clock;


    public AuthenticatorHTTP(Environment environment, Credentials credentials) {
        this(Objects.requireNonNull(environment, "ambiente").getUrl(), credentials, TIMEOUT_PATTERN);
    }

    public AuthenticatorHTTP(String baseUrl, Credentials credentials, Duration timeout) {
        this(baseUrl, credentials, buildClient(credentials), timeout);
    }

    public AuthenticatorHTTP(String baseUrl, Credentials credentials, HttpClient http, Duration timeout) {
        this(baseUrl, credentials, http, timeout, Clock.systemUTC());
    }

    public AuthenticatorHTTP(String baseUrl, Credentials credentials, HttpClient http, Duration timeout,
                             Clock clock) {
        Objects.requireNonNull(baseUrl, "baseUrl");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.client = Objects.requireNonNull(http, "http");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.clock = Objects.requireNonNull(clock, "relogio");
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.uri = URI.create(base + AUTH_PATH);
    }

    private static HttpClient buildClient(Credentials credentials) {
        Objects.requireNonNull(credentials, "credentials");
        HttpClient.Builder b = HttpClient.newBuilder()
                .connectTimeout(CONNECTION_TIMEOUT);

        if (credentials.sslContext() != null) {
            b.sslContext(credentials.sslContext());
        }

        return b.build();
    }


    @Override
    public Tokens authenticate() throws SiscomexException {
        Optional<Duration> maintenance = MaintenanceWindow.remainingAt(clock.instant());
        if (maintenance.isPresent()) {
            LOG.info("Autenticação não enviada: o portal está em parada programada.");
            throw PortalFailures.portalError(PortalErrorCode.MAINTENANCE_WINDOW, null, maintenance.get());
        }

        HttpRequest.Builder request = HttpRequest.newBuilder(this.uri)
                .timeout(this.timeout)
                .header("Role-Type", credentials.roleType().name())
                .headers("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody());

        credentials.authHeaders().forEach(request::header);
        HttpResponse<String> response;

        try {
            response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            LOG.warn("Falha de comunicação durante a autenticação: {}", e.getMessage());
            throw PortalFailures.communicationFailure(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SiscomexException("Interrompido durante a autenticação.", e);
        }

        if (response.statusCode() != 200) {
            throw httpFailure(response.statusCode(), response.body());
        }

        String jwt = response.headers().firstValue("Set-Token").orElse(null);

        if (jwt == null || jwt.isBlank()) {
            throw new SiscomexException("A resposta da autenticação não retornou o token JWT. ");
        }

        String csrfToken = response.headers().firstValue("X-CSRF-Token").orElse(null);

        if (csrfToken == null || csrfToken.isBlank()) {
            throw new SiscomexException("A resposta da autenticação não retornou o Ttoken CSRF. ");
        }

        LOG.debug("Autenticação concluída no portal.");
        return new Tokens(jwt, csrfToken);

    }

    private SiscomexException httpFailure(int status, String body) {
        AuthResponseError error = parseError(body);

        if (error == null) {
            LOG.warn("Autenticação recusada pelo portal (HTTP {}) sem detalhes.", status);
            return new SiscomexException("Falha na autenticação no portal (HTTP " + status
                    + ") sem detalhes do erro.", null, status >= 500);
        }

        Optional<PortalErrorCode> code = PortalErrorCode.fromCode(error.code());

        if (code.isEmpty()) {
            LOG.warn("Autenticação recusada pelo portal com código não mapeado: {}", error.code());
            return new SiscomexException("Erro na autenticação no portal (código " + error.code()
                    + "). Mensagem do portal: " + error.message(), error);
        }

        LOG.warn("Autenticação recusada pelo portal: {}", code.get().code());
        return PortalFailures.portalError(code.get(), error, retryAfterFor(code.get()));
    }

    private Duration retryAfterFor(PortalErrorCode code) {
        return switch (code) {
            case AUTH_RATE_LIMIT -> RATE_LIMIT_INTERVAL;
            case MAINTENANCE_WINDOW -> MaintenanceWindow.remainingAt(clock.instant()).orElse(null);
            default -> null;
        };
    }

    /**
     * Lê o corpo de erro no formato {@link AuthResponseError}. Corpos vazios ou que não são JSON
     * (por exemplo, páginas de erro de proxy) resultam em {@code null}.
     */
    private static AuthResponseError parseError(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }

        try {
            return MAPPER.readValue(body, AuthResponseError.class);
        } catch (JacksonException e) {
            return null;
        }
    }

}
