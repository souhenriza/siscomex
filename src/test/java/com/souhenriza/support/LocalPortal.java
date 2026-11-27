package com.souhenriza.support;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.security.KeyStore;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Servidor HTTPS local que simula o portal. O certificado do servidor é emitido pela CA de teste
 * ({@code tls/server.p12}); {@link #httpClient()} devolve um cliente que confia nessa CA, como o
 * cliente de produção faz com o ArmazemDeConfianca informado. Registra cada requisição recebida e responde
 * com o que o teste configurou.
 */
public final class LocalPortal implements AutoCloseable {

    public record Request(String method, String path, Map<String, String> headers, String body) {
        public String header(String name) {
            return headers.get(name);
        }
    }

    public record Reply(int status, String body, String setToken, String tokenCsrf) {
    }

    private final HttpServer server;
    private final String scheme;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final Map<String, Deque<Reply>> routes = new ConcurrentHashMap<>();
    private volatile Reply reply = new Reply(200, "", "jwt-padrao", "csrf-padrao");

    /** Servidor HTTPS com o certificado de teste do servidor. */
    public LocalPortal() throws IOException {
        this(serverTls());
    }

    /** Servidor HTTPS com o {@code tls} informado. */
    public LocalPortal(SSLContext tls) throws IOException {
        java.util.Objects.requireNonNull(tls, "tls");
        HttpsServer https = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
        https.setHttpsConfigurator(new HttpsConfigurator(tls));
        server = https;
        scheme = "https";
        server.createContext("/", exchange -> {
            Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            exchange.getRequestHeaders().forEach((name, values) -> headers.put(name, values.getFirst()));
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), headers,
                    requestBody));

            Reply r = nextReply(exchange.getRequestURI().getPath());
            if (r.setToken() != null) {
                exchange.getResponseHeaders().add("Set-Token", r.setToken());
            }
            if (r.tokenCsrf() != null) {
                exchange.getResponseHeaders().add("X-CSRF-Token", r.tokenCsrf());
            }
            exchange.getResponseHeaders().add("Content-Type", "application/json");

            byte[] body = r.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(r.status(), body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
            exchange.close();
        });
        server.start();
    }

    public void replyWith(Reply next) {
        this.reply = next;
    }

    public void replyWithTokens(String jwt, String csrf) {
        this.reply = new Reply(200, "", jwt, csrf);
    }

    public void replyWithError(int status, String body) {
        this.reply = new Reply(status, body, null, null);
    }

    /**
     * Respostas para um caminho específico, em ordem: cada requisição consome a próxima e a última
     * resposta da sequência se repete. Caminhos sem rota usam a resposta padrão.
     */
    public void replyForPath(String path, Reply... sequence) {
        routes.put(path, new ArrayDeque<>(List.of(sequence)));
    }

    private Reply nextReply(String path) {
        Deque<Reply> queue = routes.get(path);
        if (queue == null) {
            return reply;
        }
        synchronized (queue) {
            return queue.size() > 1 ? queue.poll() : queue.peek();
        }
    }

    /** Cliente HTTP que confia na CA de teste do servidor (e só nela). */
    public HttpClient httpClient() {
        return HttpClient.newBuilder().sslContext(clientTls()).build();
    }

    private static SSLContext serverTls() throws IOException {
        try {
            KeyStore keyStore = Fixtures.keyStore("tls/server.p12");
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, Fixtures.password());
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(kmf.getKeyManagers(), null, null);
            return context;
        } catch (Exception e) {
            throw new IOException("Não foi possível carregar o certificado de teste do servidor", e);
        }
    }

    private static SSLContext clientTls() {
        try {
            KeyStore trust = KeyStore.getInstance("PKCS12");
            try (var in = java.nio.file.Files.newInputStream(Fixtures.pkcs12("tls/ca-truststore.p12"))) {
                trust.load(in, Fixtures.trustStorePassword());
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trust);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, tmf.getTrustManagers(), null);
            return context;
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível carregar a CA de teste", e);
        }
    }

    public String url() {
        return scheme + "://localhost:" + server.getAddress().getPort();
    }

    public List<Request> requests() {
        return List.copyOf(requests);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
