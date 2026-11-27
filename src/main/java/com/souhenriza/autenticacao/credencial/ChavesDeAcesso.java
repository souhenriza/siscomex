package com.souhenriza.autenticacao.credencial;

import com.souhenriza.autenticacao.PerfilAcesso;

import javax.net.ssl.SSLContext;
import java.util.Map;
import java.util.Objects;

/**
 * Credenciais de usuário por par de chaves de acesso, geradas pelo próprio usuário no portal. Dispensam o
 * certificado digital: o par vai nos headers da chamada {@code POST /portal/api/autenticar/chave-acesso}, junto
 * com o {@code Role-Type}.
 * <p>
 * A autenticação de sistema integrado por chave ({@code System-Code}) não faz parte desta biblioteca: é de uso
 * restrito aos órgãos da administração pública.
 * <p>
 * O segredo ({@code Client-Secret}) nunca aparece em {@link #toString()}.
 */
public class ChavesDeAcesso implements Credenciais {

    /** Endpoint de autenticação do usuário por chave de acesso. */
    public static final String CAMINHO_CHAVE_ACESSO = "/portal/api/autenticar/chave-acesso";

    private static final String CABECALHO_CLIENT_ID = "Client-Id";
    private static final String CABECALHO_CLIENT_SECRET = "Client-Secret";
    private static final String MASCARA = "***";

    private final PerfilAcesso perfil;
    private final String chaveIdentificacao;
    private final String chaveSecreta;

    /**
     * Credenciais de usuário por chave de acesso.
     *
     * @param perfil             perfil de acesso, não nulo
     * @param chaveIdentificacao chave de identificação do usuário ({@code Client-Id}), não vazia
     * @param chaveSecreta       chave secreta ({@code Client-Secret}), não vazia
     * @throws NullPointerException     se algum argumento for nulo
     * @throws IllegalArgumentException se alguma chave estiver vazia
     */
    public ChavesDeAcesso(PerfilAcesso perfil, String chaveIdentificacao, String chaveSecreta) {
        this.perfil = Objects.requireNonNull(perfil, "perfil");
        this.chaveIdentificacao = exigirTexto(chaveIdentificacao, "chave de identificação do usuário");
        this.chaveSecreta = exigirTexto(chaveSecreta, "chave secreta");
    }

    /**
     * Credenciais de usuário por chave de acesso.
     *
     * @param chaveIdentificacaoDoUsuario chave de identificação ({@code Client-Id}), não vazia
     * @param chaveSecreta                chave secreta ({@code Client-Secret}), não vazia
     * @param perfilAcesso                perfil de acesso, não nulo
     * @return credenciais de usuário (tipo não genérico)
     * @throws NullPointerException     se algum argumento for nulo
     * @throws IllegalArgumentException se alguma chave estiver vazia
     */
    public static ChavesDeAcesso deChaveDeAcesso(String chaveIdentificacaoDoUsuario, String chaveSecreta,
                                                 PerfilAcesso perfilAcesso) {
        return new ChavesDeAcesso(perfilAcesso, chaveIdentificacaoDoUsuario, chaveSecreta);
    }

    /**
     * Valida um texto obrigatório: não nulo e não vazio.
     *
     * @param valor     texto a validar
     * @param descricao nome do campo, usado na mensagem de erro
     * @return o próprio valor (tipo não genérico)
     * @throws NullPointerException     se {@code valor} for nulo
     * @throws IllegalArgumentException se {@code valor} for vazio ou só tiver espaços
     */
    private static String exigirTexto(String valor, String descricao) {
        Objects.requireNonNull(valor, descricao);
        if (valor.isBlank()) {
            throw new IllegalArgumentException(descricao + " não pode ser vazia.");
        }
        return valor;
    }

    /**
     * Esta autenticação não usa certificado digital: a conexão usa a configuração TLS padrão da JVM.
     *
     * @return {@code null} (tipo não genérico)
     */
    @Override
    public SSLContext contextoSsl() {
        return null;
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
     * Endpoint de autenticação do usuário por chave de acesso.
     *
     * @return {@link #CAMINHO_CHAVE_ACESSO} (tipo não genérico)
     */
    @Override
    public String caminhoAutenticacao() {
        return CAMINHO_CHAVE_ACESSO;
    }

    /**
     * Headers da chave: {@code Client-Id} e {@code Client-Secret}.
     *
     * @return mapa não modificável de headers (tipo genérico {@code Map<String, String>})
     */
    @Override
    public Map<String, String> cabecalhosAutenticacao() {
        return Map.of(CABECALHO_CLIENT_ID, chaveIdentificacao, CABECALHO_CLIENT_SECRET, chaveSecreta);
    }

    /**
     * Resumo seguro para log: o segredo é mascarado.
     *
     * @return texto com perfil, identificação e {@code clientSecret=***} (tipo não genérico)
     */
    @Override
    public String toString() {
        return "ChavesDeAcesso[perfil=" + perfil + ", clientId=" + chaveIdentificacao
                + ", clientSecret=" + MASCARA + "]";
    }
}
