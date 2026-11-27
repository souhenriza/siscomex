package com.souhenriza.autenticacao.credencial;

import com.souhenriza.autenticacao.PerfilAcesso;
import com.souhenriza.autenticacao.certificado.IdentificadorCertificado;
import com.souhenriza.autenticacao.certificado.TipoCertificadoICPBrasil;
import com.souhenriza.autenticacao.erro.CodigoErroPortal;
import com.souhenriza.autenticacao.erro.ExcecaoSiscomex;

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

/**
 * Credenciais baseadas em certificado digital A1 (arquivo .pfx), e-CPF ou e-CNPJ, com o perfil de acesso.
 * <p>
 * Use {@link #deArquivo(Path, char[], PerfilAcesso)} para carregar o arquivo: a validade e o tipo do certificado são
 * conferidos antes de qualquer chamada de rede.
 */
public class CertificadoA1 implements Credenciais {

    private static final ZoneId FUSO_BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final SSLContext contextoSsl;
    private final PerfilAcesso perfil;

    /**
     * Cria as credenciais a partir de um contexto TLS já montado.
     *
     * @param contextoSsl contexto TLS com o certificado do cliente
     * @param perfil      perfil de acesso, não nulo
     * @throws NullPointerException if {@code perfil} for nulo
     */
    public CertificadoA1(SSLContext contextoSsl, PerfilAcesso perfil) {
        this.contextoSsl = contextoSsl;
        this.perfil = Objects.requireNonNull(perfil, "perfil");
    }

    /**
     * Carrega o certificado A1 e valida o servidor do portal contra o TrustStore padrão da JVM.
     *
     * @param pfx        caminho do arquivo .pfx
     * @param senha      senha do arquivo; é apagada da memória ao final
     * @param perfil     perfil de acesso, não nulo
     * @return credenciais prontas para uso (tipo não genérico)
     * @throws NullPointerException if {@code perfil} for nulo
     * @throws ExcecaoSiscomex      se o arquivo não puder ser lido, a senha estiver errada, o certificado estiver
     *                              vencido, não tiver chave privada, não for e-CPF/e-CNPJ, ou não for aceito pelo perfil
     */
    public static CertificadoA1 deArquivo(Path pfx, char[] senha, PerfilAcesso perfil) {
        return deArquivo(pfx, senha, perfil, TrustStore.padrao());
    }

    /**
     * Carrega o certificado A1 e valida o servidor do portal contra o armazém de confiança informado.
     *
     * @param pfx      caminho do arquivo .pfx
     * @param senha    senha do arquivo; é apagada da memória ao final
     * @param perfil   perfil de acesso, não nulo
     * @param armazem  armazém de certificados confiáveis para validar o servidor, não nulo
     * @return credenciais prontas para uso (tipo não genérico)
     * @throws NullPointerException if {@code perfil} ou {@code armazem} for nulo
     * @throws ExcecaoSiscomex      nas mesmas situações de {@link #deArquivo(Path, char[], PerfilAcesso)}
     */
    public static CertificadoA1 deArquivo(Path pfx, char[] senha, PerfilAcesso perfil, TrustStore armazem) {
        Objects.requireNonNull(perfil, "perfil");
        Objects.requireNonNull(armazem, "armazem");
        try {
            KeyStore armazemChaves = KeyStore.getInstance("PKCS12");

            try (InputStream entrada = Files.newInputStream(pfx)) {
                armazemChaves.load(entrada, senha);
            } catch (IOException e) {
                throw new ExcecaoSiscomex("Falha ao carregar o certificado digital: senha incorreta ou arquivo inválido.", e);
            }

            validarTipoCertificado(armazemChaves, perfil);

            KeyManagerFactory gerenciadorChaves = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());

            gerenciadorChaves.init(armazemChaves, senha);

            SSLContext contexto = SSLContext.getInstance("TLS");

            contexto.init(gerenciadorChaves.getKeyManagers(), armazem.gerenciadoresConfianca(), null);

            return new CertificadoA1(contexto, perfil);
        } catch (ExcecaoSiscomex e) {
            throw e;
        } catch (Exception e) {
            throw new ExcecaoSiscomex("Falha ao carregar o certificado digital.", e);
        } finally {
            if (senha != null) {
                Arrays.fill(senha, '\0');
            }
        }
    }

    /**
     * Confere, antes de qualquer chamada de rede, se o certificado está dentro da validade e se é e-CPF ou e-CNPJ
     * aceito pelo perfil. A falta de aceitação equivale ao erro {@code PLAT-ER2008} do portal.
     *
     * @param armazem armazém carregado do arquivo .pfx
     * @param perfil  perfil de acesso
     * @throws Exception             se o arquivo não puder ser lido como certificado
     * @throws ExcecaoSiscomex       se o certificado estiver vencido ou ainda não válido, se não tiver chave privada,
     *                               se não for ICP-Brasil, ou se o perfil não aceitar o tipo
     */
    private static void validarTipoCertificado(KeyStore armazem, PerfilAcesso perfil) throws Exception {
        X509Certificate certificado = buscarCertificadoComChavePrivada(armazem);

        if (certificado == null) {
            throw new ExcecaoSiscomex("Não foi encontrado certificado com chave privada no arquivo informado.");
        }

        try {
            certificado.checkValidity();
        } catch (CertificateExpiredException e) {
            throw new ExcecaoSiscomex("O certificado está vencido (validade até " + formatarData(certificado.getNotAfter()) + ").", e);
        } catch (CertificateNotYetValidException e) {
            throw new ExcecaoSiscomex("O certificado ainda não está válido (início em " + formatarData(certificado.getNotBefore()) + ").", e);
        }

        TipoCertificadoICPBrasil tipo;
        try {
            tipo = IdentificadorCertificado.identificar(certificado);
        } catch (Exception e) {
            throw new ExcecaoSiscomex("O certificado informado não é um e-CPF ou e-CNPJ ICP-Brasil.", e);
        }

        if (perfil.aceita(tipo)) {
            return;
        }

        if (tipo == TipoCertificadoICPBrasil.ECNPJ) {
            throw new ExcecaoSiscomex(CodigoErroPortal.ECNPJ_NAO_PERMITIDO_PARA_PERFIL.codigo() + ": A autenticação com e-CNPJ não é válida para o perfil "
                    + perfil.name() + ". Para esse perfil deve ser utilizado um certificado do tipo e-CPF.",
                    null, CodigoErroPortal.ECNPJ_NAO_PERMITIDO_PARA_PERFIL, null);
        }

        throw new ExcecaoSiscomex("A autenticação com e-CPF não é válida para o perfil " + perfil.name()
                + ". Para esse perfil deve ser utilizado um certificado do tipo e-CNPJ.");
    }

    /**
     * Data no formato brasileiro, no fuso de Brasília.
     *
     * @param data data a formatar
     * @return data no formato {@code dd/MM/yyyy} (tipo não genérico)
     */
    private static String formatarData(Date data) {
        return data.toInstant().atZone(FUSO_BRASILIA).toLocalDate().format(FORMATO_DATA);
    }

    /**
     * Retorna o certificado da única chave privada do arquivo. Um arquivo com mais de uma chave é recusado, porque o
     * certificado validado precisa ser exatamente o que será apresentado ao portal.
     *
     * @param armazem armazém carregado do arquivo
     * @return certificado da chave privada, ou {@code null} se o arquivo não tiver chave privada (tipo não genérico)
     * @throws Exception       se o armazém não puder ser lido
     * @throws ExcecaoSiscomex se o arquivo tiver mais de uma chave privada
     */
    private static X509Certificate buscarCertificadoComChavePrivada(KeyStore armazem) throws Exception {
        List<String> aliasesChave = new ArrayList<>();
        for (String alias : Collections.list(armazem.aliases())) {
            if (armazem.isKeyEntry(alias)) {
                aliasesChave.add(alias);
            }
        }

        if (aliasesChave.isEmpty()) {
            return null;
        }

        if (aliasesChave.size() > 1) {
            throw new ExcecaoSiscomex("O arquivo contém mais de uma chave privada. "
                    + "Informe um arquivo com apenas o certificado de autenticação.");
        }

        return (X509Certificate) armazem.getCertificate(aliasesChave.get(0));
    }

    /**
     * Contexto TLS com o certificado do cliente.
     *
     * @return contexto TLS (tipo não genérico)
     */
    @Override
    public SSLContext contextoSsl() {
        return contextoSsl;
    }

    /**
     * Perfil de acesso informado na criação.
     *
     * @return perfil (tipo não genérico)
     */
    @Override
    public PerfilAcesso perfil() {
        return perfil;
    }

    /**
     * Esta implementação não envia cabeçalhos extras de autenticação: o certificado já identifica o cliente.
     *
     * @return mapa vazio (tipo genérico {@code Map<String, String>})
     */
    @Override
    public Map<String, String> cabecalhosAutenticacao() {
        return Map.of();
    }
}
