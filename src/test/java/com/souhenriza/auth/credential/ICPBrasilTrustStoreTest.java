package com.souhenriza.auth.credential;

import com.souhenriza.support.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ICPBrasilTrustStoreTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("Pula arquivos corrompidos e mantém os certificados válidos do diretório")
    void skipsInvalidFilesAndKeepsValidOnes() throws Exception {
        X509Certificate valid = Fixtures.certificate("ecpf.p12");
        Files.write(dir.resolve("valido.crt"), valid.getEncoded());
        Files.write(dir.resolve("corrompido.crt"), "isto não é um certificado".getBytes(StandardCharsets.UTF_8));
        Files.write(dir.resolve("vazio.cer"), new byte[0]);

        List<X509Certificate> loaded = ICPBrasilTrustStore.readCertsDir(dir);

        assertEquals(1, loaded.size());
        assertEquals(valid, loaded.get(0));
    }

    @Test
    @DisplayName("Ignora arquivos com extensão não suportada, mesmo contendo certificado válido")
    void ignoresUnsupportedExtensions() throws Exception {
        X509Certificate valid = Fixtures.certificate("ecpf.p12");
        Files.write(dir.resolve("certificado.txt"), valid.getEncoded());
        Files.write(dir.resolve("valido.der"), valid.getEncoded());

        List<X509Certificate> loaded = ICPBrasilTrustStore.readCertsDir(dir);

        assertEquals(1, loaded.size());
        assertTrue(loaded.get(0).getSubjectX500Principal().getName().contains("CN="));
    }

    @Test
    @DisplayName("Diretório sem nenhum arquivo válido devolve lista vazia sem interromper a execução")
    void directoryWithOnlyInvalidFilesReturnsEmptyList() throws Exception {
        Files.write(dir.resolve("corrompido.crt"), "lixo".getBytes(StandardCharsets.UTF_8));

        assertEquals(0, ICPBrasilTrustStore.readCertsDir(dir).size());
    }
}
