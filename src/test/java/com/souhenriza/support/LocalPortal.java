package com.souhenriza.support;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Servidor HTTP local que simula o portal. Registra cada requisição recebida e responde
 * com o que o teste configurou.
 */
public final class LocalPortal implements AutoCloseable {

    public record Request(String method, String path, Map<String, String> headers) {
        public String header(String name) {
            return headers.get(name);
        }
    }

    public record Reply(int status, String body, String setToken, String csrfToken) {
    }

    private final HttpServer server;
    private final String scheme;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final Map<String, Deque<Reply>> routes = new ConcurrentHashMap<>();
    private volatile Reply reply = new Reply(200, "", "jwt-padrao", "csrf-padrao");

    /** Servidor HTTP simples, sem TLS. */
    public LocalPortal() throws IOException {
        this(null);
    }

    /**
     * Servidor HTTPS quando {@code tls} é informado; caso contrário, HTTP simples.
     */
    public LocalPortal(SSLContext tls) throws IOException {
        if (tls == null) {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            scheme = "http";
        } else {
            HttpsServer https = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
            https.setHttpsConfigurator(new HttpsConfigurator(tls));
            server = https;
            scheme = "https";
        }
        server.createContext("/", exchange -> {
            Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            exchange.getRequestHeaders().forEach((name, values) -> headers.put(name, values.getFirst()));
            requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), headers));

            Reply r = nextReply(exchange.getRequestURI().getPath());
            if (r.setToken() != null) {
                exchange.getResponseHeaders().add("Set-Token", r.setToken());
            }
            if (r.csrfToken() != null) {
                exchange.getResponseHeaders().add("X-CSRF-Token", r.csrfToken());
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
