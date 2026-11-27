package com.souhenriza.support;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Collections;

/** Acesso aos certificados de teste em {@code src/test/resources/certificates}.
 * Certificados sem validade perante à ICP Brasil.  */
public final class Fixtures {

    public static final String PASSWORD = "senha";

    private Fixtures() {
    }

    public static char[] password() {
        return PASSWORD.toCharArray();
    }

    /** Senha dos arquivos de ArmazemDeConfianca de teste (o keytool exige ao menos 6 caracteres). */
    public static char[] trustStorePassword() {
        return "confianca".toCharArray();
    }

    public static Path pkcs12(String name) {
        URL url = Fixtures.class.getResource("/certificates/" + name);
        if (url == null) {
            throw new IllegalArgumentException("Certificado de teste não encontrado: " + name);
        }
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    public static KeyStore keyStore(String name) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (var in = Files.newInputStream(pkcs12(name))) {
            ks.load(in, password());
        }
        return ks;
    }

    public static X509Certificate certificate(String name) throws Exception {
        KeyStore ks = keyStore(name);
        return (X509Certificate) ks.getCertificate(Collections.list(ks.aliases()).get(0));
    }
}
