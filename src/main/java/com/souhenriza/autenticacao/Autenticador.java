package com.souhenriza.autenticacao;

import com.souhenriza.autenticacao.erro.ExcecaoSiscomex;
import com.souhenriza.autenticacao.sessao.Tokens;

/**
 * Obtém os tokens de sessão do portal. A implementação padrão é o {@link AutenticadorHTTP}.
 */
public interface Autenticador {

    /**
     * Realiza a autenticação no portal e devolve os tokens da nova sessão.
     *
     * @return tokens de autenticação (JWT) e CSRF (tipo não genérico)
     * @throws ExcecaoSiscomex se a autenticação for recusada ou não puder ser concluída
     */
    Tokens autenticar() throws ExcecaoSiscomex;
}
