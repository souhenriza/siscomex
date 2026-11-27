package com.souhenriza.autenticacao.credencial;

import com.souhenriza.autenticacao.PerfilAcesso;

import javax.net.ssl.SSLContext;
import java.util.Map;

/**
 * Credenciais usadas para autenticar no portal: o contexto TLS com o certificado do cliente, o perfil de acesso e
 * cabeçalhos extras de autenticação, quando houver.
 */
public interface Credenciais {

    /** Caminho do endpoint de autenticação por certificado digital (mTLS), a partir da base URL. */
    String CAMINHO_AUTENTICACAO_CERTIFICADO = "/portal/api/autenticar";

    /**
     * Caminho do endpoint de autenticação que corresponde a estas credenciais. O padrão é o endpoint de
     * certificado digital; as credenciais por chave de acesso sobrescrevem este método.
     *
     * @return caminho a partir da base URL, começando com {@code /} (tipo não genérico)
     */
    default String caminhoAutenticacao() {
        return CAMINHO_AUTENTICACAO_CERTIFICADO;
    }

    /**
     * Contexto TLS com o certificado do cliente (mTLS).
     *
     * @return contexto TLS, ou {@code null} para usar a configuração padrão da JVM (tipo não genérico)
     */
    SSLContext contextoSsl();

    /**
     * Perfil de acesso enviado no cabeçalho {@code Role-Type}.
     *
     * @return perfil (tipo não genérico)
     */
    PerfilAcesso perfil();

    /**
     * Cabeçalhos extras enviados na autenticação, além do {@code Role-Type}.
     *
     * @return mapa de cabeçalhos, possivelmente vazio (tipo genérico {@code Map<String, String>})
     */
    Map<String, String> cabecalhosAutenticacao();
}
