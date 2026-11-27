package com.souhenriza.autenticacao.api;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Resposta de um serviço do portal. Os nomes dos cabeçalhos são comparados sem diferenciar maiúsculas.
 *
 * @param status      código HTTP
 * @param corpo       corpo da resposta, como texto
 * @param cabecalhos primeiro valor de cada cabeçalho da resposta
 */
public record RespostaSiscomex(int status, String corpo, Map<String, String> cabecalhos) {

    /** Cabeçalhos cujos valores são tokens de sessão e nunca aparecem em texto. */
    private static final Set<String> CABECALHOS_SENSIVEIS = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    static {
        CABECALHOS_SENSIVEIS.addAll(List.of("Set-Token", "X-CSRF-Token", "Authorization", "Cookie", "Set-Cookie"));
    }

    /**
     * Copia os cabeçalhos para um mapa não modificável, sem diferenciar maiúsculas.
     *
     * @throws NullPointerException se algum argumento de {@code cabecalhos} for nulo
     */
    public RespostaSiscomex {
        Map<String, String> copia = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (cabecalhos != null) {
            copia.putAll(cabecalhos);
        }
        cabecalhos = Collections.unmodifiableMap(copia);
    }

    /**
     * Indica se o status está na faixa de sucesso (2xx).
     *
     * @return {@code true} para 200 a 299 (tipo não genérico)
     */
    public boolean ehSucesso() {
        return status >= 200 && status < 300;
    }

    /**
     * Valor de um cabeçalho da resposta.
     *
     * @param nome nome do cabeçalho, sem diferenciar maiúsculas
     * @return valor, ou vazio se o cabeçalho não existir (tipo genérico {@code Optional<String>})
     */
    public Optional<String> cabecalho(String nome) {
        return Optional.ofNullable(cabecalhos.get(nome));
    }

    /**
     * Resumo seguro para log. Os cabeçalhos de sessão (JWT e CSRF) aparecem mascarados com {@code ***}, para que um
     * log da resposta não exponha tokens.
     *
     * @return texto no formato {@code RespostaSiscomex[status=..., corpo=..., cabecalhos=...]} (tipo não genérico)
     */
    @Override
    public String toString() {
        Map<String, String> mascarados = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        cabecalhos.forEach((nome, valor) -> mascarados.put(nome, CABECALHOS_SENSIVEIS.contains(nome) ? "***" : valor));
        return "RespostaSiscomex[status=" + status + ", corpo=" + corpo + ", cabecalhos=" + mascarados + "]";
    }
}
