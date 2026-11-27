package com.souhenriza.autenticacao;

import com.souhenriza.autenticacao.certificado.TipoCertificadoICPBrasil;
import com.souhenriza.autenticacao.erro.ExcecaoSiscomex;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/**
 * Perfis de atuação aceitos no cabeçalho {@code Role-Type} da autenticação do Portal Único.
 * <p>
 * Os nomes das constantes são os valores enviados ao portal e não podem ser alterados. Fonte: documentação
 * "Autenticação - API - Portal Único Siscomex", seção "Perfis de Acesso (Role-Type)".
 */
public enum PerfilAcesso {

    AGECARGA("Agente de Carga", true, false),
    AGEREMESS("Remessa Expressa/Correio", true, true),
    AJUDESPAC("Ajudante de Despachante", true, false),
    CONTATOOEA("Ponto de Contato OEA", true, false),
    DEPOSIT("Depositário", true, true),
    EMPRCOMEL("Empresa de Comércio Eletrônico", true, true),
    HABILITAD("Habilitador", true, false),
    IMPEXP("Declarante Importador/Exportador", true, false),
    INSTFINANC("Instituição Financeira", true, false),
    OPERPORT("Operador Portuário", true, true),
    RESPLEGAL("Responsável Legal OEA", true, false),
    TRANSPEST("PF – Representante de TETI", true, false),
    TRANSPORT("Transportador", true, true),
    TERCEIROS("Terceiros (Outros Intervenientes)", false, true);

    /** Código de erro do portal para cabeçalho ausente. */
    static final String ERRO_AUSENTE = "PUCX-ER0101";

    /** Código de erro do portal para cabeçalho inválido. */
    static final String ERRO_INVALIDO = "PUCX-ER0102";

    private final String descricao;
    private final boolean aceitaECpf;
    private final boolean aceitaECnpj;

    /**
     * Cria um perfil com sua descrição e os tipos de certificado que admite.
     *
     * @param descricao   nome do perfil em português, para exibição
     * @param aceitaECpf  {@code true} se o perfil admite e-CPF
     * @param aceitaECnpj {@code true} se o perfil admite e-CNPJ
     */
    PerfilAcesso(String descricao, boolean aceitaECpf, boolean aceitaECnpj) {
        this.descricao = descricao;
        this.aceitaECpf = aceitaECpf;
        this.aceitaECnpj = aceitaECnpj;
    }

    /**
     * Nome do perfil em português.
     *
     * @return descrição do perfil (tipo não genérico)
     */
    public String descricao() {
        return descricao;
    }

    /**
     * Indica se o perfil admite certificado de pessoa física (e-CPF).
     *
     * @return {@code true} se admite e-CPF (tipo não genérico)
     */
    public boolean aceitaECpf() {
        return aceitaECpf;
    }

    /**
     * Indica se o perfil admite certificado de pessoa jurídica (e-CNPJ).
     *
     * @return {@code true} se admite e-CNPJ (tipo não genérico)
     */
    public boolean aceitaECnpj() {
        return aceitaECnpj;
    }

    /**
     * Indica se o perfil aceita o tipo de certificado informado.
     *
     * @param tipo tipo do certificado, não nulo
     * @return {@code true} se o perfil aceita esse tipo (tipo não genérico)
     * @throws NullPointerException se {@code tipo} for nulo
     */
    public boolean aceita(TipoCertificadoICPBrasil tipo) {
        Objects.requireNonNull(tipo, "tipo");
        return switch (tipo) {
            case ECPF -> aceitaECpf;
            case ECNPJ -> aceitaECnpj;
        };
    }

    /**
     * Converte o texto informado pelo chamador para o perfil, antes de qualquer chamada de rede. A comparação
     * ignora espaços e diferenças de maiúsculas e minúsculas.
     *
     * @param valor código do perfil, por exemplo {@code "IMPEXP"}
     * @return perfil correspondente (tipo não genérico)
     * @throws ExcecaoSiscomex se o valor for nulo ou vazio ({@code PUCX-ER0101}), ou se não estiver na lista
     *                         do portal ({@code PUCX-ER0102})
     */
    public static PerfilAcesso de(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ExcecaoSiscomex(ERRO_AUSENTE + ": o cabeçalho 'Role-Type' não foi informado.");
        }

        String normalizado = valor.trim().toUpperCase(Locale.ROOT);

        return Arrays.stream(values())
                .filter(perfil -> perfil.name().equals(normalizado))
                .findFirst()
                .orElseThrow(() -> new ExcecaoSiscomex(
                        ERRO_INVALIDO + ": o cabeçalho 'Role-Type' é inválido: '" + valor
                                + "'. Use um dos valores da tabela de Perfis de Acesso."));
    }
}
