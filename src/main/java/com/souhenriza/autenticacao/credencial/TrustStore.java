package com.souhenriza.autenticacao.credencial;

import com.souhenriza.autenticacao.erro.ExcecaoSiscomex;

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
 * Conjunto de certificados de autoridades confiáveis usado para validar o certificado do servidor do portal
 * durante o handshake mTLS.
 * <p>
 * Sem arquivo informado, usa o armazém padrão da JVM (configurável por {@code javax.net.ssl.trustStore}). Com
 * arquivo, usa apenas os certificados dele, o que permite incluir a cadeia ICP-Brasil e a do SERPRO sem depender
 * da configuração da máquina.
 */
public final class TrustStore {

    /** Armazém padrão: sem gerenciadores próprios, a JVM usa o dela. */
    private static final TrustStore PADRAO = new TrustStore(null);

    /** {@code null} significa "usar o armazém padrão da JVM". */
    private final TrustManager[] gerenciadoresConfianca;

    /**
     * Cria o armazém com os gerenciadores de confiança já montados.
     *
     * @param gerenciadoresConfianca gerenciadores de confiança, ou {@code null} para o padrão da JVM
     */
    private TrustStore(TrustManager[] gerenciadoresConfianca) {
        this.gerenciadoresConfianca = gerenciadoresConfianca;
    }

    /**
     * Armazém padrão da JVM.
     *
     * @return armazém padrão (tipo não genérico)
     */
    public static TrustStore padrao() {
        return PADRAO;
    }

    /**
     * Carrega um arquivo PKCS#12 com os certificados confiáveis. Certificados com chave privada também são aceitos
     * como âncora. A senha é apagada da memória ao final da leitura.
     *
     * @param arquivo caminho do arquivo .p12 com as autoridades confiáveis, não nulo
     * @param senha   senha do arquivo; pode ser {@code null} se o arquivo não exigir senha
     * @return armazém com os certificados do arquivo (tipo não genérico)
     * @throws NullPointerException if {@code arquivo} for nulo
     * @throws ExcecaoSiscomex      se o arquivo não puder ser lido, a senha estiver errada, ou o arquivo não tiver
     *                              certificados confiáveis
     */
    public static TrustStore deArquivo(Path arquivo, char[] senha) {
        Objects.requireNonNull(arquivo, "arquivo");
        try {
            KeyStore armazem = KeyStore.getInstance("PKCS12");
            try (InputStream entrada = Files.newInputStream(arquivo)) {
                armazem.load(entrada, senha);
            }

            if (!possuiCertificadoConfiavel(armazem)) {
                throw new ExcecaoSiscomex("O TrustStore informado não contém certificados confiáveis.");
            }

            TrustManagerFactory fabrica = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            fabrica.init(armazem);
            return new TrustStore(fabrica.getTrustManagers());
        } catch (IOException | GeneralSecurityException e) {
            throw new ExcecaoSiscomex("Falha ao carregar o TrustStore informado.", e);
        } finally {
            if (senha != null) {
                Arrays.fill(senha, '\0');
            }
        }
    }

    /**
     * Indica se o armazém tem ao menos uma entrada de certificado confiável.
     *
     * @param armazem armazém já carregado
     * @return {@code true} se houver algum certificado (tipo não genérico)
     * @throws GeneralSecurityException se os aliases não puderem ser lidos
     */
    private static boolean possuiCertificadoConfiavel(KeyStore armazem) throws GeneralSecurityException {
        for (String alias : Collections.list(armazem.aliases())) {
            if (armazem.isCertificateEntry(alias)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gerenciadores de confiança a serem usados no {@code SSLContext}.
     *
     * @return gerenciadores, ou {@code null} para usar o padrão da JVM (tipo não genérico)
     */
    TrustManager[] gerenciadoresConfianca() {
        return gerenciadoresConfianca;
    }
}
