package com.souhenriza.autenticacao.credencial;

import com.souhenriza.autenticacao.PerfilAcesso;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes unitários de {@link ChavesDeAcesso}: endpoint, headers, validação e mascaramento. Não usam rede. */
class ChavesDeAcessoTest {

    @Test
    @DisplayName("Chave de usuário usa o endpoint /chave-acesso e envia só Client-Id e Client-Secret")
    void userKeyUsesUserEndpointAndTwoHeaders() {
        ChavesDeAcesso chaves = ChavesDeAcesso.deChaveDeAcesso("id-usuario", "segredo", PerfilAcesso.AGECARGA);

        assertEquals("/portal/api/autenticar/chave-acesso", chaves.caminhoAutenticacao());
        assertEquals(Map.of("Client-Id", "id-usuario", "Client-Secret", "segredo"), chaves.cabecalhosAutenticacao());
        assertEquals(PerfilAcesso.AGECARGA, chaves.perfil());
    }

    @Test
    @DisplayName("Chave de usuário não usa certificado digital: contexto TLS nulo")
    void userKeyHasNoClientCertificate() {
        assertNull(ChavesDeAcesso.deChaveDeAcesso("id", "segredo", PerfilAcesso.IMPEXP).contextoSsl());
    }

    @Test
    @DisplayName("Credenciais por certificado mantêm o endpoint mTLS como padrão")
    void certificateCredentialsKeepDefaultEndpoint() {
        Credenciais semChave = new Credenciais() {
            @Override
            public javax.net.ssl.SSLContext contextoSsl() {
                return null;
            }

            @Override
            public PerfilAcesso perfil() {
                return PerfilAcesso.AGECARGA;
            }

            @Override
            public Map<String, String> cabecalhosAutenticacao() {
                return Map.of();
            }
        };

        assertEquals("/portal/api/autenticar", semChave.caminhoAutenticacao());
    }

    @Test
    @DisplayName("Client-Id ou Client-Secret vazios são recusados")
    void blankKeysAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ChavesDeAcesso.deChaveDeAcesso("   ", "segredo", PerfilAcesso.AGECARGA));
        assertThrows(IllegalArgumentException.class,
                () -> ChavesDeAcesso.deChaveDeAcesso("id", "", PerfilAcesso.AGECARGA));
    }

    @Test
    @DisplayName("Chaves e perfil nulos são recusados")
    void nullValuesAreRejected() {
        assertThrows(NullPointerException.class, () -> ChavesDeAcesso.deChaveDeAcesso(null, "segredo", PerfilAcesso.AGECARGA));
        assertThrows(NullPointerException.class, () -> ChavesDeAcesso.deChaveDeAcesso("id", null, PerfilAcesso.AGECARGA));
        assertThrows(NullPointerException.class, () -> ChavesDeAcesso.deChaveDeAcesso("id", "segredo", null));
    }

    @Test
    @DisplayName("toString mostra o Client-Id, mas nunca o segredo")
    void toStringMasksSecret() {
        String texto = ChavesDeAcesso.deChaveDeAcesso("id-visivel", "segredo-oculto", PerfilAcesso.AGECARGA).toString();

        assertTrue(texto.contains("id-visivel"), texto);
        assertTrue(texto.contains("clientSecret=***"), texto);
        assertFalse(texto.contains("segredo-oculto"), texto);
    }

    @Test
    @DisplayName("Mapa de cabeçalhos devolvido é não modificável")
    void headersAreUnmodifiable() {
        Map<String, String> cabecalhos = ChavesDeAcesso.deChaveDeAcesso("id", "segredo", PerfilAcesso.AGECARGA)
                .cabecalhosAutenticacao();

        assertThrows(UnsupportedOperationException.class, () -> cabecalhos.put("X", "y"));
    }
}
