package com.souhenriza.duimp;

/** Versão de uma DUIMP, de 0 a 9999 (limites informados na documentação do portal). */
public record VersaoDuimp(int valor) {

    public static final int MINIMO = 0;
    public static final int MAXIMO = 9999;

    public VersaoDuimp {
        if (valor < MINIMO || valor > MAXIMO) {
            throw new IllegalArgumentException("Versão da DUIMP fora do intervalo " + MINIMO + "-" + MAXIMO + ": " + valor);
        }
    }

    public static VersaoDuimp of(int valor) {
        return new VersaoDuimp(valor);
    }
}
