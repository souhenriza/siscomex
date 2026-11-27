package com.souhenriza.duimp;

import java.io.Serial;
import java.util.List;

/**
 * A capa da DUIMP não passou na validação local e não foi enviada ao portal. Traz todas as violações
 * encontradas, cada uma no formato {@code caminho.do.campo: motivo}.
 */
public class DuimpValidacaoException extends DuimpException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final List<String> violacoes;

    public DuimpValidacaoException(List<String> violacoes) {
        super("Capa da DUIMP inválida:\n - " + String.join("\n - ", violacoes));
        this.violacoes = List.copyOf(violacoes);
    }

    public List<String> violacoes() {
        return violacoes;
    }
}
