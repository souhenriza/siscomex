package com.souhenriza.auth;

import com.souhenriza.auth.cert.ITIBrasilCertType;
import com.souhenriza.error.SiscomexException;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/**
 * Perfis de atuação aceitos no cabeçalho {@code Role-Type} da autenticação do Portal Único.
 * Fonte: documentação "Autenticação - API - Portal Único Siscomex", seção "Perfis de Acesso (Role-Type)".
 */
public enum RoleType {

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

    private final String description;
    private final boolean allowECpf;
    private final boolean allowECnpj;

    RoleType(String description, boolean allowECpf, boolean allowECnpj) {
        this.description = description;
        this.allowECpf = allowECpf;
        this.allowECnpj = allowECnpj;
    }

    public String description() {
        return description;
    }

    /** Indica se o perfil admite certificado de pessoa física (e-CPF). */
    public boolean allowECpf() {
        return allowECpf;
    }

    /** Indica se o perfil admite certificado de pessoa jurídica (e-CNPJ). */
    public boolean allowECnpj() {
        return allowECnpj;
    }

    /** Indica se o perfil aceita o tipo de certificado informado. */
    public boolean accepts(ITIBrasilCertType type) {
        Objects.requireNonNull(type, "tipo");
        return switch (type) {
            case ECPF -> allowECpf;
            case ECNPJ -> allowECnpj;
        };
    }

    /**
     * Converte o valor informado pelo chamador para o perfil, antes de qualquer chamada de rede.
     *
     * @throws SiscomexException {@code PUCX-ER0101} se o valor for nulo ou vazio;
     *                           {@code PUCX-ER0102} se o valor não estiver na lista do portal.
     */
    public static RoleType from(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new SiscomexException(ERRO_AUSENTE + ": o cabeçalho 'Role-Type' não foi informado.");
        }

        String normalizado = valor.trim().toUpperCase(Locale.ROOT);

        return Arrays.stream(values())
                .filter(r -> r.name().equals(normalizado))
                .findFirst()
                .orElseThrow(() -> new SiscomexException(
                        ERRO_INVALIDO + ": o cabeçalho 'Role-Type' é inválido: '" + valor
                                + "'. Use um dos valores da tabela de Perfis de Acesso."));
    }
}
