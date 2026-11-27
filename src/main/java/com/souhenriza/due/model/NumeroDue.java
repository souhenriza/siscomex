package com.souhenriza.due.model;

import com.souhenriza.due.DueException;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Número da DUE no formato {@code NNAANNNNNNNNNN} 2 dígitos numéricos, 2 dígitos de ano, 10 dígitos sequenciais.
 * <p>
 *    Validado antes de montar o caminho que utilizará o número da DUE como parâmetro. Valida o texto para que não chegue arbitrariedades à URL.
 *    Segundo a documentação, não há digitos verificadores no número da DUE.
 */
public record NumeroDue(
        String valor
) {

    private static final Pattern FORMATO = Pattern.compile("\\d{2}[A-Z]{2}\\d{10}");

    public NumeroDue {
        Objects.requireNonNull(valor, "numero da DUE");
        if (!FORMATO.matcher(valor).matches()) {
            throw new DueException("Número da Due inválido. Espera-se um formato de 14 caracteres no formato NNAANNNNNNNNNN. ");
        }
    }

    public static NumeroDue of(String valor)
    {
        return new NumeroDue(valor);
    }

    @Override
    public String toString() {
        return "*".repeat(valor.length() -1);
    }
}



