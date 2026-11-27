package com.souhenriza.autenticacao;

import com.souhenriza.autenticacao.credencial.CertificadoA1;
import com.souhenriza.autenticacao.credencial.TrustStore;
import com.souhenriza.autenticacao.erro.ExcecaoSiscomex;
import com.souhenriza.autenticacao.sessao.Tokens;
import com.souhenriza.support.Fixtures;
import com.souhenriza.support.LocalPortal;
import com.souhenriza.support.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Handshake TLS real entre o cliente e um portal HTTPS local. O certificado do servidor é emitido
 * por uma CA de teste; o cliente só o aceita quando essa CA está no ArmazemDeConfianca informado.
 */
class AutenticadorHTTPTlsTest {

    /** 12:00 em Brasília, fora da janela de parada. */
    private static final Instant NOON = Instant.parse("2026-11-15T15:00:00Z");

    private LocalPortal portal;

    @BeforeEach
    void startTlsPortal() throws Exception {
        portal = new LocalPortal(serverTls());
    }

    @AfterEach
    void stopPortal() {
        portal.close();
    }

    @Test
    @DisplayName("Autentica quando a CA do servidor está no ArmazemDeConfianca informado")
    void authenticatesWhenServerCaIsTrusted() throws Exception {
        portal.replyWithTokens("jwt-tls", "csrf-tls");

        Tokens tokens = authenticatorWith(Fixtures.pkcs12("tls/ca-truststore.p12")).autenticar();

        assertEquals("jwt-tls", tokens.tokenAutenticacao());
        assertEquals("csrf-tls", tokens.tokenCsrf());
        assertEquals(1, portal.requests().size());
    }

    @Test
    @DisplayName("Recusa o servidor quando a CA dele não está no ArmazemDeConfianca padrão da JVM")
    void rejectsServerWhenCaIsNotInJvmDefault() throws Exception {
        ExcecaoSiscomex error = assertThrows(ExcecaoSiscomex.class,
                () -> authenticatorWith(null).autenticar());

        assertTrue(error.getMessage().startsWith("Falha TLS"), error::getMessage);
        assertEquals(0, portal.requests().size(), "a requisição não pode chegar ao portal");
    }

    @Test
    @DisplayName("Recusa o servidor quando o ArmazemDeConfianca contém apenas outra CA")
    void rejectsServerWhenTrustStoreHasOtherCa() throws Exception {
        ExcecaoSiscomex error = assertThrows(ExcecaoSiscomex.class,
                () -> authenticatorWith(Fixtures.pkcs12("tls/other-ca-truststore.p12")).autenticar());

        assertTrue(error.getMessage().startsWith("Falha TLS"), error::getMessage);
        assertEquals(0, portal.requests().size(), "a requisição não pode chegar ao portal");
    }

    /**
     * Cliente com o e-CPF de teste e o ArmazemDeConfianca informado. Quando {@code trustStoreFile} é nulo,
     * usa o ArmazemDeConfianca padrão da JVM.
     */
    private AutenticadorHTTP authenticatorWith(Path trustStoreFile) throws Exception {
        TrustStore trustStore = trustStoreFile == null
                ? TrustStore.padrao()
                : TrustStore.deArquivo(trustStoreFile, Fixtures.trustStorePassword());

        CertificadoA1 credentials = CertificadoA1.deArquivo(Fixtures.pkcs12("ecpf.p12"), Fixtures.password(),
                PerfilAcesso.AGECARGA, trustStore);

        HttpClient client = HttpClient.newBuilder().sslContext(credentials.contextoSsl()).build();

        return new AutenticadorHTTP(portal.url(), credentials, client, Duration.ofSeconds(5),
                new MutableClock(NOON));
    }

    private static SSLContext serverTls() throws Exception {
        KeyStore keyStore = Fixtures.keyStore("tls/server.p12");

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, Fixtures.password());

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), null, null);
        return context;
    }
}
