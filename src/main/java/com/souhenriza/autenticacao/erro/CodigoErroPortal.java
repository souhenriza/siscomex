package com.souhenriza.autenticacao.erro;

import java.util.Optional;

/**
 * Códigos de erro retornados pelo Portal Único na autenticação.
 * <p>
 * Os nomes das constantes Java são em português; o código devolvido pelo portal ({@link #codigo()}) é o que
 * identifica o erro e não muda. Fonte: documentação "Autenticação - API - Portal Único Siscomex", seção
 * "Códigos de erro".
 */
public enum CodigoErroPortal {

    CABECALHO_PERFIL_AUSENTE("PUCX-ER0101", Categoria.PERFIL, false,
            "O cabeçalho Role-Type não foi enviado na requisição."),

    CABECALHO_PERFIL_INVALIDO("PUCX-ER0102", Categoria.PERFIL, false,
            "O cabeçalho Role-Type é inválido. Verifique a tabela de Perfis de Acesso."),

    PARADA_PROGRAMADA("PUCX-ER0401", Categoria.MANUTENCAO, true,
            "O portal está em parada programada, diariamente entre 01:00 e 03:00."),

    HANDSHAKE_MTLS_FALHOU("PLAT-ER2001", Categoria.CERTIFICADO, false,
            "Não foi possível identificar um certificado digital válido. O handshake mTLS não foi efetuado com sucesso."),

    ECNPJ_NAO_PERMITIDO_PARA_PERFIL("PLAT-ER2008", Categoria.CERTIFICADO, false,
            "A autenticação com e-CNPJ não é válida para o perfil selecionado. Para esse perfil deve ser utilizado um certificado do tipo e-CPF."),

    SEM_REPRESENTACAO_NO_PERFIL("PLAT-ER2013", Categoria.PERFIL, false,
            "Usuário não possui representações no perfil selecionado. Consulte as informações sobre acesso a sistemas aduaneiros."),

    CNPJ_NAO_HABILITADO_PARA_PERFIL("PLAT-ER2023", Categoria.PERFIL, false,
            "O CNPJ não possui habilitação para atuar no perfil selecionado. Consulte as informações sobre acesso a sistemas aduaneiros."),

    LIMITE_AUTENTICACAO("PLAT-ER2033", Categoria.LIMITE_TAXA, true,
            "Operação não autorizada. Já houve uma autenticação válida nos últimos 60 segundos."),

    CPF_IRREGULAR("PLAT-ER3005", Categoria.USUARIO, false,
            "O CPF do usuário não está em situação regular."),

    CERTIFICADO_EQUIPAMENTO_NAO_IDENTIFICADO("PLAT-ER8001", Categoria.CERTIFICADO, false,
            "Não foi possível identificar um certificado digital de equipamento válido."),

    CERTIFICADO_EQUIPAMENTO_NAO_HABILITADO("PLAT-ER8002", Categoria.CERTIFICADO, false,
            "O certificado digital não está habilitado no Portal Único do Comércio Exterior. A habilitação deve ser solicitada à SECEX."),

    CHAVE_ACESSO_INVALIDA("PLAT-ER8003", Categoria.CHAVE_ACESSO, false,
            "A chave de acesso enviada não é válida. Verifique o System-Code enviado."),

    AUTENTICACAO_CHAVE_ACESSO_FALHOU("PLAT-ER8008", Categoria.CHAVE_ACESSO, false,
            "Não foi possível efetuar a autenticação com chave de acesso.");

    /** Área do erro, para o chamador decidir como tratá-lo. */
    public enum Categoria {
        PERFIL,
        CERTIFICADO,
        LIMITE_TAXA,
        MANUTENCAO,
        CHAVE_ACESSO,
        USUARIO
    }

    private final String codigo;
    private final Categoria categoria;
    private final boolean repetivel;
    private final String descricao;

    /**
     * Cria a constante de erro.
     *
     * @param codigo     código devolvido pelo portal, por exemplo {@code PUCX-ER0101}
     * @param categoria  área do erro
     * @param repetivel  {@code true} se a operação pode ser repetida após o intervalo informado
     * @param descricao  texto explicativo em português
     */
    CodigoErroPortal(String codigo, Categoria categoria, boolean repetivel, String descricao) {
        this.codigo = codigo;
        this.categoria = categoria;
        this.repetivel = repetivel;
        this.descricao = descricao;
    }

    /**
     * Código do erro como devolvido pelo portal.
     *
     * @return código, por exemplo {@code PLAT-ER2033} (tipo não genérico)
     */
    public String codigo() {
        return codigo;
    }

    /**
     * Área do erro.
     *
     * @return categoria (tipo não genérico)
     */
    public Categoria categoria() {
        return categoria;
    }

    /**
     * Indica se a operação pode ser repetida, após o intervalo informado.
     *
     * @return {@code true} se pode ser repetida (tipo não genérico)
     */
    public boolean repetivel() {
        return repetivel;
    }

    /**
     * Texto explicativo do erro.
     *
     * @return descrição em português (tipo não genérico)
     */
    public String descricao() {
        return descricao;
    }

    /**
     * Localiza a constante pelo código devolvido pelo portal.
     *
     * @param codigo código do portal; pode ser nulo
     * @return a constante correspondente, ou vazio quando o código é nulo ou desconhecido
     *         (tipo genérico {@code Optional<CodigoErroPortal>})
     */
    public static Optional<CodigoErroPortal> deCodigo(String codigo) {
        if (codigo == null) {
            return Optional.empty();
        }
        for (CodigoErroPortal valor : values()) {
            if (valor.codigo.equals(codigo)) {
                return Optional.of(valor);
            }
        }
        return Optional.empty();
    }
}
