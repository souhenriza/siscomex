package com.souhenriza.duimp.model.importador;

import com.souhenriza.duimp.DuimpException;

/**
 * Faixa de itens a consultar: a partir de {@code inicial}, até {@code tamanho} itens.
 * O portal aceita no máximo 100 itens por requisição.
 */
public record IntervaloItens(int inicial, int tamanho) {

    public static final int TAMANHO_MAXIMO = 100;
    public static final int INICIAL_PADRAO = 1;
    public static final int TAMANHO_PADRAO = TAMANHO_MAXIMO;

    public IntervaloItens {
        if (inicial < 1 || inicial > NumeroItem.MAXIMO) {
            throw new DuimpException("Item inicial fora do intervalo 1-" + NumeroItem.MAXIMO + ": " + inicial);
        }
        if (tamanho < 1 || tamanho > TAMANHO_MAXIMO) {
            throw new DuimpException("Tamanho da faixa fora do intervalo 1-" + TAMANHO_MAXIMO + ": " + tamanho);
        }
    }

    /** Faixa padrão do portal: itens 1 a 100. */
    public static IntervaloItens padrao() {
        return new IntervaloItens(INICIAL_PADRAO, TAMANHO_PADRAO);
    }
}
