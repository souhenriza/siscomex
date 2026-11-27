package com.souhenriza.duimp.model.importador;

import com.souhenriza.duimp.DuimpException;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Número da DUIMP no formato {@code AABRSSSSSSSSSSD}: 2 dígitos de ano, {@code BR}, 10 dígitos sequenciais e
 * 1 dígito verificador. Validado antes de montar qualquer caminho, para que texto arbitrário não chegue à URL.
 * <p>
 * O dígito verificador não é conferido: a fórmula módulo 11 não está descrita de forma precisa na documentação
 * do portal.
 */
public record NumeroDuimp(String valor) {

    private static final Pattern FORMATO = Pattern.compile("\\d{2}BR\\d{11}");

    public NumeroDuimp {
        Objects.requireNonNull(valor, "numero da DUIMP");
        if (!FORMATO.matcher(valor).matches()) {
            throw new DuimpException(
                    "Número de DUIMP inválido: esperado o formato AABRSSSSSSSSSSD (15 caractere). ");
        }
    }

    public static NumeroDuimp of(String valor) {
        return new NumeroDuimp(valor);
    }

    @Override
    public String toString() {

        return "*".repeat(valor.length() - 1);
    }
}
