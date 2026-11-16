package com.souhenriza.auth.api;

import com.souhenriza.auth.MaintenanceWindow;
import com.souhenriza.auth.PortalUrl;
import com.souhenriza.auth.error.PortalErrorCode;
import com.souhenriza.auth.error.PortalFailures;
import com.souhenriza.auth.error.SiscomexException;
import com.souhenriza.auth.session.Session;
import com.souhenriza.auth.session.SessionManagement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Envia requisições autenticadas aos serviços do portal.
 * <p>
 * Para cada chamada, acrescenta {@code Authorization} (JWT) e {@code X-CSRF-Token}, atualiza o CSRF com o
 * valor devolvido em cada resposta e, se o portal responder 401, autentica de novo e repete a chamada uma vez.
 * Respostas com outros códigos (incluindo 403, 404, 422 e 500) são devolvidas ao chamador, porque a
 * interpretação dos erros de negócio depende de cada serviço.
 */
public class ApiClient {

    private static final Logger LOG = LoggerFactory.getLogger(ApiClient.class);

    private static final int UNAUTHORIZED = 401;
    private static final Pattern IMPORTER_NI = Pattern.compile("(/chaves-acesso/importadores/)[^/?]+");
    private static final Duration SERVICE_TIMEOUT = Duration.ofSeconds(60);

    private final String baseUrl;
    private final HttpClient http;
    private final SessionManagement sessions;
    private final Clock clock;

    public ApiClient(String baseUrl, SessionManagement sessions, HttpClient http, Clock clock) {
        this.baseUrl = PortalUrl.requireHttps(baseUrl);
        this.sessions = Objects.requireNonNull(sessions, "sessoes");
        this.http = Objects.requireNonNull(http, "http");
        this.clock = Objects.requireNonNull(clock, "relogio");
    }

    /**
     * Envia a requisição ao portal.
     *
     * @throws SiscomexException se o portal estiver em parada programada, se a comunicação falhar ou se o
     *                           portal rejeitar a sessão mesmo após nova autenticação
     */
    public SiscomexResponse send(SiscomexRequest request) {
        Objects.requireNonNull(request, "requisicao");

        Optional<Duration> maintenance = MaintenanceWindow.remainingAt(clock.instant());
        if (maintenance.isPresent()) {
            LOG.info("Requisição {} {} não enviada: o portal está em parada programada.", request.method(), redactPath(request.path()));
            throw PortalFailures.portalError(PortalErrorCode.MAINTENANCE_WINDOW, null, maintenance.get());
        }

        SiscomexResponse response = sessions.execute(
                session -> call(request, session),
                ApiClient::sessionRejectedAndLogged);

        if (response.status() == UNAUTHORIZED) {
            LOG.warn("O portal rejeitou a sessão de {} {} mesmo após nova autenticação.", request.method(), redactPath(request.path()));
            throw new SiscomexException("O portal rejeitou a sessão mesmo após nova autenticação (HTTP 401).");
        }

        return response;
    }

    private SiscomexResponse call(SiscomexRequest request, Session session) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + request.path()))
                .timeout(SERVICE_TIMEOUT)
                .header("Accept", "application/json")
                .header("Authorization", session.authToken())
                .header("X-CSRF-Token", session.csrfToken());

        HttpRequest.BodyPublisher publisher;
        if (request.body() == null) {
            publisher = HttpRequest.BodyPublishers.noBody();
        } else {
            builder.header("Content-Type", "application/json");
            publisher = HttpRequest.BodyPublishers.ofString(request.body());
        }
        builder.method(request.method(), publisher);

        HttpResponse<String> raw;
        try {
            raw = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            LOG.warn("Falha de comunicação em {} {}: {}", request.method(), redactPath(request.path()), e.getMessage());
            throw PortalFailures.communicationFailure(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SiscomexException("Interrompido durante a chamada ao portal.", e);
        }

        SiscomexResponse response = new SiscomexResponse(raw.statusCode(), raw.body(), firstValues(raw.headers()));

        if (response.status() != UNAUTHORIZED) {
            sessions.updateFromResponse(null, response.header("X-CSRF-Token").orElse(null));
        }

        LOG.debug("{} {} respondeu HTTP {}", request.method(), redactPath(request.path()), response.status());
        return response;
    }

    /**
     * Versão do caminho segura para log: o número de identificação do importador (CPF ou CNPJ), que aparece
     * em {@code /chaves-acesso/importadores/{ni}}, é substituído por {@code ***}.
     */
    public static String redactPath(String path) {
        return IMPORTER_NI.matcher(path).replaceAll("$1***");
    }

    private static boolean sessionRejectedAndLogged(SiscomexResponse response) {
        if (response.status() == UNAUTHORIZED) {
            LOG.info("Sessão rejeitada pelo portal (HTTP 401). Renovando a autenticação e repetindo a chamada.");
            return true;
        }
        return false;
    }

    private static Map<String, String> firstValues(HttpHeaders headers) {
        Map<String, String> values = new HashMap<>();
        headers.map().forEach((name, list) -> {
            if (!list.isEmpty()) {
                values.put(name, list.get(0));
            }
        });
        return values;
    }

    /** Cliente HTTP padrão para os serviços, com o mesmo {@code SSLContext} da autenticação. */
    public static HttpClient defaultHttpClient(SSLContext sslContext) {
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20));
        if (sslContext != null) {
            builder.sslContext(sslContext);
        }
        return builder.build();
    }
}
