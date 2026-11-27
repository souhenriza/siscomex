package com.souhenriza.http;

import javax.net.ssl.SSLContext;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Fábrica do {@link HttpClient} usado para falar com o portal. Concentra a configuração comum (tempo de
 * conexão e {@link SSLContext} com o certificado do cliente) para que autenticação e serviços compartilhem
 * um único cliente, com um só pool de conexões.
 */
public final class PortalHttpClients {

    /** Tempo máximo para abrir a conexão TCP/TLS com o portal. */
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(20);

    /**
     * Construtor privado: a classe é um utilitário sem estado e não é instanciada.
     */
    private PortalHttpClients() {
    }

    /**
     * Cria um cliente com o tempo de conexão padrão e, se informado, o {@code SSLContext} do certificado.
     *
     * @param sslContext contexto TLS com o certificado do cliente e o ArmazemDeConfianca; pode ser {@code null},
     *                   caso em que a JVM usa a configuração padrão
     * @return cliente novo, não compartilhado com outras chamadas (tipo não genérico)
     */
    public static HttpClient create(SSLContext sslContext) {
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT);
        if (sslContext != null) {
            builder.sslContext(sslContext);
        }
        return builder.build();
    }
}
