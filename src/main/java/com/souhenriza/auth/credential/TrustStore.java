package com.souhenriza.auth.credential;

import com.souhenriza.error.SiscomexException;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

/**
 * Conjunto de certificados de autoridades confiáveis usado para validar o certificado do servidor
 * do portal durante o handshake mTLS.
 * <p>
 * Sem arquivo informado, usa o TrustStore padrão da JVM (configurável por {@code javax.net.ssl.trustStore}).
 * Com arquivo, usa apenas os certificados dele, o que permite incluir a cadeia ICP-Brasil e a do SERPRO
 * sem depender da configuração da máquina.
 */
public final class TrustStore {

    private static final TrustStore SYSTEM = new TrustStore(null);

    /** {@code null} significa "usar o TrustStore padrão da JVM". */
    private final TrustManager[] trustManagers;

    private TrustStore(TrustManager[] trustManagers) {
        this.trustManagers = trustManagers;
    }

    /** TrustStore padrão da JVM. */
    public static TrustStore system() {
        return SYSTEM;
    }

    /**
     * Carrega um arquivo PKCS#12 com os certificados confiáveis. Certificados com chave privada
     * também são aceitos como âncora. A senha é zerada ao final da leitura.
     *
     * @param file     caminho do arquivo .p12 com as autoridades confiáveis
     * @param password senha do arquivo; pode ser {@code null} se o arquivo não exigir senha
     */
    public static TrustStore from(Path file, char[] password) {
        Objects.requireNonNull(file, "arquivo");
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (InputStream in = Files.newInputStream(file)) {
                ks.load(in, password);
            }

            if (!hasTrustedCertificate(ks)) {
                throw new SiscomexException("O TrustStore informado não contém certificados confiáveis.");
            }

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            return new TrustStore(tmf.getTrustManagers());
        } catch (IOException | GeneralSecurityException e) {
            throw new SiscomexException("Falha ao carregar o TrustStore informado.", e);
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }
    }

    private static boolean hasTrustedCertificate(KeyStore ks) throws GeneralSecurityException {
        for (String alias : Collections.list(ks.aliases())) {
            if (ks.isCertificateEntry(alias)) {
                return true;
            }
        }
        return false;
    }

    /** Gerenciadores de confiança a serem usados no {@code SSLContext}; {@code null} usa o padrão da JVM. */
    TrustManager[] trustManagers() {
        return trustManagers;
    }
}
