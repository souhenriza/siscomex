package com.souhenriza.autenticacao.credencial;

import com.souhenriza.autenticacao.erro.ExcecaoSiscomex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Montagem de um armazém de confiança com as autoridades da JVM e os certificados ICP-Brasil de uma pasta.
 *
 * @deprecated use {@link TrustStore#deArquivo(Path, char[])} com um arquivo PKCS#12 único.
 */
@Deprecated
public class ArmazemDeConfiancaICPBrasil {

    private static final Logger LOG = LoggerFactory.getLogger(ArmazemDeConfiancaICPBrasil.class);
    private static final String[] EXTENSOES_PERMITIDAS = {".pem", ".crt", ".cer", ".der"};

    /**
     * Construtor privado: a classe é um utilitário estático.
     */
    private ArmazemDeConfiancaICPBrasil() {
    }

    /**
     * Monta um armazém PKCS#12 com as autoridades confiáveis da JVM e os certificados informados.
     *
     * @param certificadosAdicionais certificados extras a confiar, não nulo
     * @return armazém pronto para uso (tipo não genérico)
     * @throws GeneralSecurityException se o armazém não puder ser criado
     * @throws NullPointerException     se {@code certificadosAdicionais} for nulo
     * @throws ExcecaoSiscomex          se não for possível montar o armazém por falha de E/S
     */
    public static KeyStore montar(Collection<X509Certificate> certificadosAdicionais) throws GeneralSecurityException {
        Objects.requireNonNull(certificadosAdicionais, "certificadosAdicionais");
        Objects.requireNonNull(emissoresPadraoJvm(), "emissoresPadraoJvm");
        try {
            KeyStore armazem = KeyStore.getInstance("PKCS12");
            armazem.load(null, null);
            int indice = 0;

            for (X509Certificate emissor : emissoresPadraoJvm()) {
                armazem.setCertificateEntry("jvm-" + (indice++), emissor);
            }

            for (X509Certificate extra : certificadosAdicionais) {
                armazem.setCertificateEntry("extra-" + impressaoDigital(extra), extra);
            }

            return armazem;
        } catch (GeneralSecurityException | IOException e) {
            throw new ExcecaoSiscomex("Falha ao montar o TrustStore");
        }
    }

    /**
     * Lê todos os certificados ({@code .pem}, {@code .crt}, {@code .cer}, {@code .der}) de uma pasta. Arquivos
     * inválidos são ignorados com aviso no log.
     *
     * @param diretorio pasta com os certificados, não nula
     * @return certificados lidos, possivelmente vazia (tipo genérico {@code List<X509Certificate>})
     * @throws NullPointerException if {@code diretorio} for nulo
     * @throws ExcecaoSiscomex      se a pasta não puder ser lida
     */
    public static List<X509Certificate> lerDiretorioCertificados(Path diretorio) {
        Objects.requireNonNull(diretorio, "diretorio");
        List<X509Certificate> todos = new ArrayList<>();

        try (DirectoryStream<Path> caminhos = Files.newDirectoryStream(diretorio)) {
            for (Path caminho : caminhos) {
                if (Files.isRegularFile(caminho) && ehExtensaoCertificado(caminho)) {
                    try (InputStream entrada = Files.newInputStream(caminho)) {
                        todos.addAll(ler(entrada));
                    } catch (CertificateException | IOException | RuntimeException e) {
                        LOG.warn("Certificado inválido ignorado: {} ({})", caminho.getFileName(), e.getMessage());
                    }
                }
            }

        } catch (IOException e) {
            throw new ExcecaoSiscomex("Não foi possível ler o diretório de certificados: " + diretorio, e);
        }

        return todos;
    }

    /**
     * Lê os certificados X.509 de um fluxo de dados.
     *
     * @param entrada fluxo com um ou mais certificados
     * @return certificados lidos (tipo genérico {@code List<X509Certificate>})
     * @throws CertificateException se o conteúdo não for um certificado X.509
     */
    private static List<X509Certificate> ler(InputStream entrada) throws CertificateException {
        CertificateFactory fabrica = CertificateFactory.getInstance("X.509");
        List<X509Certificate> resultado = new ArrayList<>();

        for (var certificado : fabrica.generateCertificates(entrada)) {
            resultado.add((X509Certificate) certificado);
        }
        return resultado;
    }

    /**
     * Impressão digital SHA-256 do certificado, em hexadecimal maiúsculo, usada para nomear a entrada no armazém.
     *
     * @param certificado certificado a identificar
     * @return impressão digital (tipo não genérico)
     * @throws ExcecaoSiscomex se o algoritmo SHA-256 não estiver disponível ou o certificado não puder ser codificado
     */
    private static String impressaoDigital(X509Certificate certificado) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(certificado.getEncoded());

            return HexFormat.of().withUpperCase().formatHex(hash);
        } catch (GeneralSecurityException e) {
            throw new ExcecaoSiscomex("Não foi possível calcular a impressão digital dos certificados da ICP Brasil. ");
        }
    }

    /**
     * Autoridades confiáveis que a JVM já aceita por padrão.
     *
     * @return certificados das autoridades padrão (tipo genérico {@code List<X509Certificate>})
     * @throws GeneralSecurityException se o armazém padrão não puder ser lido
     */
    private static List<X509Certificate> emissoresPadraoJvm() throws GeneralSecurityException {
        TrustManagerFactory fabrica = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

        fabrica.init((KeyStore) null);

        List<X509Certificate> resultado = new ArrayList<>();

        for (TrustManager gerenciador : fabrica.getTrustManagers()) {
            if (gerenciador instanceof X509TrustManager x509) {
                resultado.addAll(List.of(x509.getAcceptedIssuers()));
            }
        }

        return resultado;
    }

    /**
     * Indica se o nome do arquivo termina com uma extensão de certificado aceita, sem diferenciar maiúsculas.
     *
     * @param caminho arquivo a verificar
     * @return {@code true} se a extensão for aceita (tipo não genérico)
     */
    private static boolean ehExtensaoCertificado(Path caminho) {
        String nome = caminho.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String extensao : EXTENSOES_PERMITIDAS) {
            if (nome.endsWith(extensao)) {
                return true;
            }
        }

        return false;
    }
}
