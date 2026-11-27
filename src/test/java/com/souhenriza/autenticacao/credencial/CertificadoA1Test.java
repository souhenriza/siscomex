package com.souhenriza.autenticacao.credencial;

import com.souhenriza.autenticacao.PerfilAcesso;
import com.souhenriza.autenticacao.erro.CodigoErroPortal;
import com.souhenriza.autenticacao.erro.ExcecaoSiscomex;
import com.souhenriza.support.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.OutputStream;
import java.security.KeyStore;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CertificadoA1Test {

    @DisplayName("Aceita e-CPF em perfil que admite somente e-CPF e expõe perfil e SSLContext")
    @Test
    void eCpfIsAcceptedByEcpfOnlyProfile() {
        CertificadoA1 certificate = CertificadoA1.deArquivo(Fixtures.pkcs12("ecpf.p12"), Fixtures.password(), PerfilAcesso.AGECARGA);

        assertEquals(PerfilAcesso.AGECARGA, certificate.perfil());
        assertNotNull(certificate.contextoSsl());
        assertEquals(Map.of(), certificate.cabecalhosAutenticacao());
    }

    @DisplayName("Aceita e-CNPJ nos perfis que admitem e-CNPJ (TERCEIROS e AGEREMESS)")
    @Test
    void eCnpjIsAcceptedByProfilesThatAllowIt() {
        assertEquals(PerfilAcesso.TERCEIROS,
                CertificadoA1.deArquivo(Fixtures.pkcs12("ecnpj.p12"), Fixtures.password(), PerfilAcesso.TERCEIROS).perfil());
        assertEquals(PerfilAcesso.AGEREMESS,
                CertificadoA1.deArquivo(Fixtures.pkcs12("ecnpj.p12"), Fixtures.password(), PerfilAcesso.AGEREMESS).perfil());
    }

    @DisplayName("Aceita e-CNPJ com CPF do responsável listado antes do CNPJ em perfil de e-CNPJ")
    @Test
    void eCnpjWithResponsibleCpfListedFirstIsAcceptedByEcnpjProfile() {
        assertEquals(PerfilAcesso.TERCEIROS,
                CertificadoA1.deArquivo(Fixtures.pkcs12("ecnpj_responsible_cpf_first.p12"), Fixtures.password(), PerfilAcesso.TERCEIROS)
                        .perfil());
    }

    @DisplayName("Recusa e-CNPJ em perfil só de e-CPF antes de qualquer chamada de rede, com código PLAT-ER2008")
    @Test
    void eCnpjIsRejectedByEcpfOnlyProfileWithPortalCode() {
        ExcecaoSiscomex e = assertThrows(ExcecaoSiscomex.class, () ->
                CertificadoA1.deArquivo(Fixtures.pkcs12("ecnpj.p12"), Fixtures.password(), PerfilAcesso.AGECARGA));

        assertEquals(Optional.of(CodigoErroPortal.ECNPJ_NAO_PERMITIDO_PARA_PERFIL), e.codigoErroPortal());
        assertTrue(e.getMessage().startsWith("PLAT-ER2008"), e.getMessage());
    }

    @DisplayName("Recusa e-CPF em perfil só de e-CNPJ, sem código do portal (não documentado)")
    @Test
    void eCpfIsRejectedByEcnpjOnlyProfileWithoutPortalCode() {
        ExcecaoSiscomex e = assertThrows(ExcecaoSiscomex.class, () ->
                CertificadoA1.deArquivo(Fixtures.pkcs12("ecpf.p12"), Fixtures.password(), PerfilAcesso.TERCEIROS));

        assertTrue(e.getMessage().contains("e-CPF"), e.getMessage());
        assertEquals(Optional.empty(), e.codigoErroPortal());
    }

    @DisplayName("Recusa certificado sem identificador ICP-Brasil na SAN, com mensagem de não identificação")
    @Test
    void certificateWithoutValidIdentifierIsRejected() {
        ExcecaoSiscomex e = assertThrows(ExcecaoSiscomex.class, () ->
                CertificadoA1.deArquivo(Fixtures.pkcs12("no_san.p12"), Fixtures.password(), PerfilAcesso.AGECARGA));

        assertTrue(e.getMessage().contains("ICP-Brasil"), e.getMessage());
    }

    @DisplayName("Recusa arquivo sem chave privada, com mensagem de chave ausente")
    @Test
    void keystoreWithoutPrivateKeyIsRejected() {
        ExcecaoSiscomex e = assertThrows(ExcecaoSiscomex.class, () ->
                CertificadoA1.deArquivo(Fixtures.pkcs12("ecpf_certificate_only.p12"), Fixtures.password(), PerfilAcesso.AGECARGA));

        assertTrue(e.getMessage().contains("chave privada"), e.getMessage());
    }

    @DisplayName("Senha incorreta falha ao carregar o PFX com ExcecaoSiscomex que preserva a causa")
    @Test
    void wrongPassphraseFailsWithCauseAndWithoutAcceptingTheCertificate() {
        ExcecaoSiscomex e = assertThrows(ExcecaoSiscomex.class, () ->
                CertificadoA1.deArquivo(Fixtures.pkcs12("ecpf.p12"), "senha-errada".toCharArray(), PerfilAcesso.AGECARGA));

        assertTrue(e.getMessage().startsWith("Falha ao carregar"), e.getMessage());
        assertTrue(e.getMessage().contains("senha incorreta"), e.getMessage());
        assertNotNull(e.getCause());
    }

    @DisplayName("Recusa certificado vencido, informando a data de validade")
    @Test
    void rejectsExpiredCertificate() {
        ExcecaoSiscomex e = assertThrows(ExcecaoSiscomex.class, () ->
                CertificadoA1.deArquivo(Fixtures.pkcs12("ecpf_expired.p12"), Fixtures.password(), PerfilAcesso.AGECARGA));

        assertTrue(e.getMessage().contains("vencido"), e.getMessage());
        assertTrue(e.getMessage().contains("31/12/2020"), e.getMessage());
    }

    @DisplayName("Recusa certificado ainda não válido, informando a data de início")
    @Test
    void rejectsCertificateNotYetValid() {
        ExcecaoSiscomex e = assertThrows(ExcecaoSiscomex.class, () ->
                CertificadoA1.deArquivo(Fixtures.pkcs12("ecpf_not_yet_valid.p12"), Fixtures.password(), PerfilAcesso.AGECARGA));

        assertTrue(e.getMessage().contains("ainda não está válido"), e.getMessage());
        assertTrue(e.getMessage().contains("31/12/2098"), e.getMessage());
    }

    @TempDir
    Path dir;

    @DisplayName("Recusa arquivo com mais de uma chave privada, para não apresentar um certificado não validado")
    @Test
    void rejectsFileWithTwoPrivateKeys() throws Exception {
        KeyStore first = Fixtures.keyStore("ecpf.p12");
        KeyStore second = Fixtures.keyStore("ecnpj.p12");
        String firstAlias = Collections.list(first.aliases()).get(0);
        String secondAlias = Collections.list(second.aliases()).get(0);

        KeyStore both = KeyStore.getInstance("PKCS12");
        both.load(null, null);
        both.setKeyEntry("primeira", first.getKey(firstAlias, Fixtures.password()), Fixtures.password(),
                first.getCertificateChain(firstAlias));
        both.setKeyEntry("segunda", second.getKey(secondAlias, Fixtures.password()), Fixtures.password(),
                second.getCertificateChain(secondAlias));

        Path file = dir.resolve("duas-chaves.p12");
        try (OutputStream out = Files.newOutputStream(file)) {
            both.store(out, Fixtures.password());
        }

        ExcecaoSiscomex e = assertThrows(ExcecaoSiscomex.class, () ->
                CertificadoA1.deArquivo(file, Fixtures.password(), PerfilAcesso.TERCEIROS));

        assertTrue(e.getMessage().contains("mais de uma chave privada"), e.getMessage());
    }

    @DisplayName("Zera a senha (char[]) após carregamento bem-sucedido")
    @Test
    void passphraseIsWipedAfterSuccessfulLoad() {
        char[] passphrase = Fixtures.password();
        CertificadoA1.deArquivo(Fixtures.pkcs12("ecpf.p12"), passphrase, PerfilAcesso.AGECARGA);

        assertArrayEquals(new char[passphrase.length], passphrase);
    }

    @DisplayName("Zera a senha (char[]) também quando o certificado é recusado")
    @Test
    void passphraseIsWipedAfterRejection() {
        char[] passphrase = Fixtures.password();
        assertThrows(ExcecaoSiscomex.class, () ->
                CertificadoA1.deArquivo(Fixtures.pkcs12("ecnpj.p12"), passphrase, PerfilAcesso.AGECARGA));

        assertArrayEquals(new char[passphrase.length], passphrase);
    }
}
