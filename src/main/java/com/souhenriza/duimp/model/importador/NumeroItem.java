package com.souhenriza.duimp.model.importador;

import com.souhenriza.duimp.DuimpException;

/** Número de um item da DUIMP: inteiro positivo de até 5 dígitos. */
public record NumeroItem(int valor) {

    public static final int MAXIMO = 99_999;

    public NumeroItem {
        if (valor < 1 || valor > MAXIMO) {
            throw new DuimpException("Número do item fora do intervalo 1-" + MAXIMO + ": " + valor);
        }
    }

    public static NumeroItem of(int valor) {
        return new NumeroItem(valor);
    }
}
