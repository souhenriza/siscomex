package com.souhenriza.error;

import java.util.Optional;

/**
 * Códigos de erro retornados pelo Portal Único na autenticação.
 * Fonte: documentação "Autenticação - API - Portal Único Siscomex", seção "Códigos de erro".
 */
public enum PortalErrorCode {

    MISSING_ROLE_TYPE("PUCX-ER0101", Category.PROFILE, false,
            "O cabeçalho Role-Type não foi enviado na requisição."),

    INVALID_ROLE_TYPE("PUCX-ER0102", Category.PROFILE, false,
            "O cabeçalho Role-Type é inválido. Verifique a tabela de Perfis de Acesso."),

    MAINTENANCE_WINDOW("PUCX-ER0401", Category.MAINTENANCE, true,
            "O portal está em parada programada, diariamente entre 01:00 e 03:00."),

    MTLS_HANDSHAKE_FAILED("PLAT-ER2001", Category.CERTIFICATE, false,
            "Não foi possível identificar um certificado digital válido. O handshake mTLS não foi efetuado com sucesso."),

    ECNPJ_NOT_ALLOWED_FOR_PROFILE("PLAT-ER2008", Category.CERTIFICATE, false,
            "A autenticação com e-CNPJ não é válida para o perfil selecionado. Para esse perfil deve ser utilizado um certificado do tipo e-CPF."),

    NO_PROFILE_REPRESENTATION("PLAT-ER2013", Category.PROFILE, false,
            "Usuário não possui representações no perfil selecionado. Consulte as informações sobre acesso a sistemas aduaneiros."),

    CNPJ_NOT_ENABLED_FOR_PROFILE("PLAT-ER2023", Category.PROFILE, false,
            "O CNPJ não possui habilitação para atuar no perfil selecionado. Consulte as informações sobre acesso a sistemas aduaneiros."),

    AUTH_RATE_LIMIT("PLAT-ER2033", Category.RATE_LIMIT, true,
            "Operação não autorizada. Já houve uma autenticação válida nos últimos 60 segundos."),

    IRREGULAR_CPF("PLAT-ER3005", Category.USER, false,
            "O CPF do usuário não está em situação regular."),

    EQUIPMENT_CERT_NOT_IDENTIFIED("PLAT-ER8001", Category.CERTIFICATE, false,
            "Não foi possível identificar um certificado digital de equipamento válido."),

    EQUIPMENT_CERT_NOT_ENABLED("PLAT-ER8002", Category.CERTIFICATE, false,
            "O certificado digital não está habilitado no Portal Único do Comércio Exterior. A habilitação deve ser solicitada à SECEX."),

    INVALID_ACCESS_KEY("PLAT-ER8003", Category.ACCESS_KEY, false,
            "A chave de acesso enviada não é válida. Verifique o System-Code enviado."),

    INTEGRATED_SYSTEM_INACTIVE("PLAT-ER8004", Category.ACCESS_KEY, false,
            "O cadastro de sistema integrado não está mais ativo. A habilitação do sistema foi desativada pela SECEX."),

    ACCESS_KEY_AUTH_FAILED("PLAT-ER8008", Category.ACCESS_KEY, false,
            "Não foi possível efetuar a autenticação com chave de acesso.");

    /** Área do erro, para o chamador decidir como tratá-lo. */
    public enum Category {
        PROFILE,
        CERTIFICATE,
        RATE_LIMIT,
        MAINTENANCE,
        ACCESS_KEY,
        USER
    }

    private final String code;
    private final Category category;
    private final boolean retryable;
    private final String description;

    PortalErrorCode(String code, Category category, boolean retryable, String description) {
        this.code = code;
        this.category = category;
        this.retryable = retryable;
        this.description = description;
    }

    public String code() {
        return code;
    }

    public Category category() {
        return category;
    }

    /** Indica se a operação pode ser repetida, após o intervalo informado por {@code retryAfter}. */
    public boolean retryable() {
        return retryable;
    }

    public String description() {
        return description;
    }

    /**
     * @return o código mapeado, ou vazio quando o portal retornou um código desconhecido.
     */
    public static Optional<PortalErrorCode> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        for (PortalErrorCode value : values()) {
            if (value.code.equals(code)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
