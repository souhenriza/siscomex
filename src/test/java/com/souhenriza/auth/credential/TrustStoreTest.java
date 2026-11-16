package com.souhenriza.auth.credential;

import com.souhenriza.error.SiscomexException;
import com.souhenriza.support.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.X509TrustManager;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrustStoreTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Sem arquivo informado, o TrustStore delega ao padrão da JVM")
    void systemTrustStoreDelegatesToJvmDefault() {
        assertNull(TrustStore.system().trustManagers());
    }

    @Test
    @DisplayName("Carrega do arquivo somente a autoridade certificadora nele contida")
    void loadsOnlyTheAuthoritiesInTheFile() {
        TrustStore trustStore = TrustStore.from(Fixtures.pkcs12("tls/ca-truststore.p12"),
                Fixtures.trustStorePassword());

        X509TrustManager manager = (X509TrustManager) trustStore.trustManagers()[0];
        var issuers = Arrays.stream(manager.getAcceptedIssuers())
                .map(c -> c.getSubjectX500Principal().getName())
                .toList();

        assertEquals(1, issuers.size());
        assertTrue(issuers.get(0).contains("CN=CA de Teste Siscomex"), issuers::toString);
    }

    @Test
    @DisplayName("Senha incorreta do arquivo do TrustStore gera SiscomexException")
    void wrongPasswordIsRejected() {
        Path file = Fixtures.pkcs12("tls/ca-truststore.p12");

        assertThrows(SiscomexException.class, () -> TrustStore.from(file, "senha-errada".toCharArray()));
    }

    @Test
    @DisplayName("Arquivo inexistente gera SiscomexException")
    void missingFileIsRejected() {
        Path missing = Path.of("arquivo-que-nao-existe.p12");

        assertThrows(SiscomexException.class, () -> TrustStore.from(missing, Fixtures.trustStorePassword()));
    }

    @Test
    @DisplayName("Arquivo sem nenhum certificado confiável é recusado ao carregar")
    void emptyTrustStoreIsRejected() throws Exception {
        Path empty = tempDir.resolve("vazio.p12");
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        try (OutputStream out = Files.newOutputStream(empty)) {
            ks.store(out, Fixtures.trustStorePassword());
        }

        SiscomexException error = assertThrows(SiscomexException.class,
                () -> TrustStore.from(empty, Fixtures.trustStorePassword()));

        assertTrue(error.getMessage().contains("não contém certificados confiáveis"), error::getMessage);
    }

    @Test
    @DisplayName("A senha informada é zerada ao final da leitura")
    void passwordIsWipedAfterLoading() {
        char[] password = Fixtures.trustStorePassword();

        TrustStore.from(Fixtures.pkcs12("tls/ca-truststore.p12"), password);

        char[] wiped = new char[password.length];
        assertArrayEquals(wiped, password);
    }
}
