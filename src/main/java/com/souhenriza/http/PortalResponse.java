package com.souhenriza.http;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Resposta HTTP recebida do Portal Único, imutável e sem interpretação de negócio.
 * <p>
 * Os nomes de header são tratados sem diferenciar maiúsculas de minúsculas, e um header pode ter vários
 * valores. A representação em texto ({@link #toString()}) mostra os headers, mas mascara os sensíveis
 * (tokens de sessão), para que um log da resposta não os exponha.
 */
public final class PortalResponse {

    /** Headers cujos valores nunca aparecem em texto (tokens de sessão). */
    private static final Set<String> SENSITIVE_HEADERS = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    static {
        SENSITIVE_HEADERS.add("Authorization");
        SENSITIVE_HEADERS.add("Set-Token");
        SENSITIVE_HEADERS.add("X-CSRF-Token");
        SENSITIVE_HEADERS.add("Cookie");
        SENSITIVE_HEADERS.add("Set-Cookie");
    }

    private final int status;
    private final String body;
    private final Map<String, List<String>> headers;

    /**
     * Cria uma resposta. A lista de valores de cada header é copiada e fica não modificável.
     *
     * @param status  código HTTP
     * @param body    corpo como texto; pode ser vazio, mas não nulo quando vem de {@link PortalHttp}
     * @param headers headers da resposta; {@code null} equivale a nenhum header
     * @throws NullPointerException se algum nome ou lista de valores de {@code headers} for nulo
     */
    public PortalResponse(int status, String body, Map<String, List<String>> headers) {
        this.status = status;
        this.body = body;
        Map<String, List<String>> copia = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (headers != null) {
            headers.forEach((nome, valores) -> {
                Objects.requireNonNull(nome, "nome do header");
                copia.put(nome, List.copyOf(Objects.requireNonNull(valores, "valores do header " + nome)));
            });
        }
        this.headers = Collections.unmodifiableMap(copia);
    }

    /**
     * Código HTTP da resposta.
     *
     * @return status, por exemplo {@code 200} ou {@code 401} (tipo não genérico)
     */
    public int status() {
        return status;
    }

    /**
     * Corpo da resposta como texto.
     *
     * @return corpo, possivelmente vazio (tipo não genérico)
     */
    public String body() {
        return body;
    }

    /**
     * Indica se o status está na faixa de sucesso (2xx).
     *
     * @return {@code true} para 200 a 299 (tipo não genérico)
     */
    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }

    /**
     * Primeiro valor de um header.
     *
     * @param nome nome do header, sem diferenciar maiúsculas
     * @return {@link Optional} com o primeiro valor, vazio se o header não existir (tipo genérico {@code Optional<String>})
     * @throws NullPointerException se {@code nome} for nulo
     */
    public Optional<String> header(String nome) {
        Objects.requireNonNull(nome, "nome do header");
        List<String> valores = headers.get(nome);
        return valores == null || valores.isEmpty() ? Optional.empty() : Optional.of(valores.getFirst());
    }

    /**
     * Todos os valores de um header, na ordem recebida.
     *
     * @param nome nome do header, sem diferenciar maiúsculas
     * @return lista não modificável, vazia se o header não existir (tipo genérico {@code List<String>})
     * @throws NullPointerException se {@code nome} for nulo
     */
    public List<String> headerValues(String nome) {
        Objects.requireNonNull(nome, "nome do header");
        return headers.getOrDefault(nome, List.of());
    }

    /**
     * Um valor por header, o primeiro de cada um. Útil para APIs que não usam headers repetidos.
     *
     * @return mapa não modificável de nome para primeiro valor, com busca sem diferenciar maiúsculas
     *         (tipo genérico {@code Map<String, String>})
     */
    public Map<String, String> firstValues() {
        Map<String, String> primeiros = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.forEach((nome, valores) -> {
            if (!valores.isEmpty()) {
                primeiros.put(nome, valores.getFirst());
            }
        });
        return Collections.unmodifiableMap(primeiros);
    }

    /**
     * Resumo seguro para log: status, corpo e headers, com os sensíveis substituídos por {@code ***}.
     *
     * @return texto no formato {@code PortalResponse[status=..., body=..., headers=...]} (tipo não genérico)
     */
    @Override
    public String toString() {
        return "PortalResponse[status=" + status + ", body=" + body + ", headers="
                + mascarados() + "]";
    }

    /**
     * Headers para exibição: cada valor de um header sensível vira {@code ***}; os demais aparecem como recebidos,
     * com valores repetidos separados por vírgula.
     *
     * @return mapa de nome para valores exibíveis, com busca sem diferenciar maiúsculas (tipo genérico {@code Map<String, String>})
     */
    private Map<String, String> mascarados() {
        Map<String, String> resumo = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.forEach((nome, valores) -> {
            List<String> visiveis = new ArrayList<>();
            for (String valor : valores) {
                visiveis.add(SENSITIVE_HEADERS.contains(nome) ? "***" : valor);
            }
            resumo.put(nome, String.join(", ", visiveis));
        });
        return resumo;
    }
}
