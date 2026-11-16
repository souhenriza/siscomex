package com.souhenriza.auth.credential;

import com.souhenriza.auth.RoleType;

import com.souhenriza.auth.cert.CertIdentifier;
import com.souhenriza.auth.cert.ITIBrasilCertType;
import com.souhenriza.auth.error.PortalErrorCode;
import com.souhenriza.auth.error.SiscomexException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class A1Certificate implements Credentials {

    private static final ZoneId BRAZIL = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter BRAZILIAN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final SSLContext sslContext;
    private final RoleType roleType;

    public A1Certificate(SSLContext sslContext, RoleType roleType) {
        this.sslContext = sslContext;
        this.roleType = Objects.requireNonNull(roleType, "perfil");
    }

    /**
     * O perfil já chega tipado: a validação do texto do cabeçalho acontece em {@link RoleType#from(String)},
     * antes de o .pfx ser lido.
     */
    public static A1Certificate from(Path pfx, char[] passphrase, RoleType roleType) {
        return from(pfx, passphrase, roleType, TrustStore.system());
    }

    /**
     * Igual a {@link #from(Path, char[], RoleType)}, mas valida o servidor do portal contra o {@code trustStore}
     * informado em vez do padrão da JVM.
     */
    public static A1Certificate from(Path pfx, char[] passphrase, RoleType roleType, TrustStore trustStore) {
        Objects.requireNonNull(roleType, "perfil");
        Objects.requireNonNull(trustStore, "truststore");
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");

            try (InputStream is = Files.newInputStream(pfx)) {
                ks.load(is, passphrase);
            } catch (IOException e) {
                throw new SiscomexException("Falha ao carregar o certificado digital: senha incorreta ou arquivo inválido.", e);
            }

            validateCertificateType(ks, roleType);

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());

            kmf.init(ks, passphrase);

            SSLContext context = SSLContext.getInstance("TLS");

            context.init(kmf.getKeyManagers(), trustStore.trustManagers(), null);

            return new A1Certificate(context, roleType);
        } catch (SiscomexException e) {
            throw e;
        } catch (Exception e) {
            throw new SiscomexException("Falha ao carregar o certificado digital.", e);
        } finally {
            if (passphrase != null) {
                Arrays.fill(passphrase, '\0');
            }
        }
    }

    /**
     * Confere, antes de qualquer chamada de rede, se o certificado está dentro da validade e se é
     * e-CPF ou e-CNPJ aceito pelo perfil. O tipo equivale ao erro {@code PLAT-ER2008} do portal.
     */
    private static void validateCertificateType(KeyStore ks, RoleType roleType) throws Exception {
        X509Certificate certificate = findPrivateKeyCertificate(ks);

        if (certificate == null) {
            throw new SiscomexException("Não foi encontrado certificado com chave privada no arquivo informado.");
        }

        try {
            certificate.checkValidity();
        } catch (CertificateExpiredException e) {
            throw new SiscomexException("O certificado está vencido (validade até " + formatDate(certificate.getNotAfter()) + ").", e);
        } catch (CertificateNotYetValidException e) {
            throw new SiscomexException("O certificado ainda não está válido (início em " + formatDate(certificate.getNotBefore()) + ").", e);
        }

        ITIBrasilCertType type;
        try {
            type = CertIdentifier.identify(certificate);
        } catch (Exception e) {
            throw new SiscomexException("O certificado informado não é um e-CPF ou e-CNPJ ICP-Brasil.", e);
        }

        if (roleType.accepts(type)) {
            return;
        }

        if (type == ITIBrasilCertType.ECNPJ) {
            throw new SiscomexException(PortalErrorCode.ECNPJ_NOT_ALLOWED_FOR_PROFILE.code() + ": A autenticação com e-CNPJ não é válida para o perfil "
                    + roleType.name() + ". Para esse perfil deve ser utilizado um certificado do tipo e-CPF.",
                    null, PortalErrorCode.ECNPJ_NOT_ALLOWED_FOR_PROFILE, null);
        }

        throw new SiscomexException("A autenticação com e-CPF não é válida para o perfil " + roleType.name()
                + ". Para esse perfil deve ser utilizado um certificado do tipo e-CNPJ.");
    }

    /** Data no formato brasileiro, no fuso de Brasília. */
    private static String formatDate(Date date) {
        return date.toInstant().atZone(BRAZIL).toLocalDate().format(BRAZILIAN_DATE);
    }

    /**
     * Retorna o certificado da única chave privada do arquivo. Um arquivo com mais de uma chave é recusado,
     * porque o certificado validado precisa ser exatamente o que será apresentado ao portal.
     */
    private static X509Certificate findPrivateKeyCertificate(KeyStore ks) throws Exception {
        List<String> keyAliases = new ArrayList<>();
        for (String alias : Collections.list(ks.aliases())) {
            if (ks.isKeyEntry(alias)) {
                keyAliases.add(alias);
            }
        }

        if (keyAliases.isEmpty()) {
            return null;
        }

        if (keyAliases.size() > 1) {
            throw new SiscomexException("O arquivo contém mais de uma chave privada. "
                    + "Informe um arquivo com apenas o certificado de autenticação.");
        }

        return (X509Certificate) ks.getCertificate(keyAliases.get(0));
    }

    @Override
    public SSLContext sslContext() {
        return sslContext;
    }

    @Override
    public RoleType roleType() {
        return this.roleType;
    }

    @Override // this impl not use authentication headers;
    public Map<String, String> authHeaders() {
        return Map.of();
    }
}
