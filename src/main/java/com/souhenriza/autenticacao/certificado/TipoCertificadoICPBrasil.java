package com.souhenriza.autenticacao.certificado;

/**
 * Tipos de certificado ICP-Brasil usados na autenticação: pessoa física ({@code ECPF}) e pessoa jurídica
 * ({@code ECNPJ}). Os nomes são os termos oficiais da ICP-Brasil.
 */
public enum TipoCertificadoICPBrasil {

    /** Certificado de pessoa física (e-CPF). */
    ECPF,

    /** Certificado de pessoa jurídica (e-CNPJ). */
    ECNPJ
}
