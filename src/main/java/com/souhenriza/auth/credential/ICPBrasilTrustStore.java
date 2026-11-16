package com.souhenriza.auth.credential;

import com.souhenriza.error.SiscomexException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;

public class ICPBrasilTrustStore {
    private static final Logger LOG = LoggerFactory.getLogger(ICPBrasilTrustStore.class);
    private static final String[] ALLOWED_EXTENSIONS = {".pem", ".crt", ".cer", ".der"};

    private ICPBrasilTrustStore(){}

    public static KeyStore build(Collection<X509Certificate> additionalCerts) throws GeneralSecurityException {
        Objects.requireNonNull(additionalCerts, "certificadosAdicionais");
        Objects.requireNonNull(patternJVMIssuers(), "patternJVMIssuers");
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, null);
            int i = 0;


            for (X509Certificate pattern : patternJVMIssuers()) {
                keyStore.setCertificateEntry("jvm-" + (i++), pattern);
            }

            for (X509Certificate extra : additionalCerts) {
                keyStore.setCertificateEntry("extra-" + fingerPrint(extra), extra);
            }

            return keyStore;
        } catch (GeneralSecurityException | IOException e) {
            throw new SiscomexException("Falha ao montar o TrustStore");
        }
    }

    public static List<X509Certificate> readCertsDir(Path dir) {
        Objects.requireNonNull(dir, "diretorio");
        List<X509Certificate> all = new ArrayList<>();

        try (DirectoryStream<Path> paths = Files.newDirectoryStream(dir)){
            for (Path path : paths) {
                if (Files.isRegularFile(path) && hasCertExtension(path)) {
                    try (InputStream in = Files.newInputStream(path)){
                        all.addAll(read(in));
                    } catch (CertificateException | IOException | RuntimeException e) {
                        LOG.warn("Certificado inválido ignorado: {} ({})", path.getFileName(), e.getMessage());
                    }
                }
            }

        } catch (IOException e) {
            throw new SiscomexException("Não foi possível ler o diretório de certificados: " + dir, e);
        }

        return all;
    }

    private static List<X509Certificate> read(InputStream in) throws CertificateException{
        CertificateFactory cF = CertificateFactory.getInstance("X.509");
        List<X509Certificate> r = new ArrayList<>();

        for (var c : cF.generateCertificates(in)) {
            r.add((X509Certificate) c);
        }
        return r;
    }

    private static String fingerPrint(X509Certificate cert) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());

            return HexFormat.of().withUpperCase().formatHex(h);
        } catch (GeneralSecurityException e) {
            throw new SiscomexException("Não foi possível calcular a impressão digital dos certificados da ICP Brasil. ");
        }
    }

    private static List<X509Certificate> patternJVMIssuers() throws GeneralSecurityException {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

        tmf.init((KeyStore) null);

        List<X509Certificate> r = new ArrayList<>();

        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager x509) {
                r.addAll(List.of(x509.getAcceptedIssuers()));
            }
        }

        return r;
    }

    private static boolean hasCertExtension(Path path) {
        String filename = path.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String ext : ALLOWED_EXTENSIONS) {
            if (filename.endsWith(ext)) {
                return true;
            }
        }

        return false;
    }
}
