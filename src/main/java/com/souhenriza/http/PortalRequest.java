package com.souhenriza.http;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Requisição HTTP ao Portal Único, imutável.
 * <p>
 * É construída por encadeamento a partir de um dos métodos estáticos ({@link #get(String)},
 * {@link #post(String)}, {@link #put(String)} ou {@link #delete(String)}):
 * <pre>{@code
 * PortalRequest.get("/due/api/ext/due/consultarDadosResumidosDUE")
 *         .param("numero", numero)          // null é ignorado
 *         .header("X-CSRF-Token", csrf);
 * }</pre>
 * Cada método de alteração devolve uma nova instância; a original não muda. Os nomes de header são
 * tratados sem diferenciar maiúsculas de minúsculas. Os cabeçalhos padrão ({@code Accept} e
 * {@code Content-Type}) não são definidos aqui: são responsabilidade de {@link PortalHttp}.
 */
public final class PortalRequest {

    private final HttpMethod method;
    private final String path;
    private final List<Parametro> parametros;
    private final Map<String, String> headers;
    private final String body;

    /**
     * Construtor privado: as instâncias são criadas pelas fábricas e pelos métodos de alteração, que já
     * validaram os argumentos e entregam coleções imutáveis.
     *
     * @param method     método HTTP, não nulo
     * @param path       caminho, começando com {@code /}
     * @param parametros parâmetros de query, na ordem de inclusão
     * @param headers    headers, com busca sem diferenciar maiúsculas
     * @param body       corpo JSON, ou {@code null} quando não há corpo
     */
    private PortalRequest(HttpMethod method, String path, List<Parametro> parametros,
                          Map<String, String> headers, String body) {
        this.method = method;
        this.path = path;
        this.parametros = parametros;
        this.headers = headers;
        this.body = body;
    }

    /**
     * Cria uma requisição {@code GET} sem corpo.
     *
     * @param path caminho a partir do host, começando com {@code /}
     * @return requisição nova (tipo não genérico)
     * @throws NullPointerException     se {@code path} for nulo
     * @throws IllegalArgumentException se {@code path} não começar com {@code /}
     */
    public static PortalRequest get(String path) {
        return of(HttpMethod.GET, path);
    }

    /**
     * Cria uma requisição {@code POST} sem corpo. Para enviar corpo, encadeie {@link #json(String)}.
     *
     * @param path caminho a partir do host, começando com {@code /}
     * @return requisição nova (tipo não genérico)
     * @throws NullPointerException     se {@code path} for nulo
     * @throws IllegalArgumentException se {@code path} não começar com {@code /}
     */
    public static PortalRequest post(String path) {
        return of(HttpMethod.POST, path);
    }

    /**
     * Cria uma requisição {@code PUT} sem corpo. Para enviar corpo, encadeie {@link #json(String)}.
     *
     * @param path caminho a partir do host, começando com {@code /}
     * @return requisição nova (tipo não genérico)
     * @throws NullPointerException     se {@code path} for nulo
     * @throws IllegalArgumentException se {@code path} não começar com {@code /}
     */
    public static PortalRequest put(String path) {
        return of(HttpMethod.PUT, path);
    }

    /**
     * Cria uma requisição {@code DELETE} sem corpo.
     *
     * @param path caminho a partir do host, começando com {@code /}
     * @return requisição nova (tipo não genérico)
     * @throws NullPointerException     se {@code path} for nulo
     * @throws IllegalArgumentException se {@code path} não começar com {@code /}
     */
    public static PortalRequest delete(String path) {
        return of(HttpMethod.DELETE, path);
    }

    /**
     * Cria uma requisição com o método informado, sem corpo.
     *
     * @param method método HTTP
     * @param path   caminho a partir do host, começando com {@code /}
     * @return requisição nova (tipo não genérico)
     * @throws NullPointerException     se {@code method} ou {@code path} for nulo
     * @throws IllegalArgumentException se {@code path} não começar com {@code /}
     */
    public static PortalRequest of(HttpMethod method, String path) {
        Objects.requireNonNull(method, "metodo");
        Objects.requireNonNull(path, "caminho");
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("O caminho do serviço deve começar com '/': " + path);
        }
        return new PortalRequest(method, path, List.of(), Map.of(), null);
    }

    /**
     * Acrescenta um parâmetro de query string. Valor nulo é ignorado, e o mesmo nome pode ser repetido.
     * O valor é convertido com {@link String#valueOf(Object)} e codificado em UTF-8 ao montar a URL.
     *
     * @param nome  nome do parâmetro, não vazio
     * @param valor valor do parâmetro; se nulo, a requisição não muda
     * @return nova requisição com o parâmetro (tipo não genérico)
     * @throws NullPointerException     se {@code nome} for nulo
     * @throws IllegalArgumentException se {@code nome} estiver vazio
     */
    public PortalRequest param(String nome, Object valor) {
        requireName(nome, "nome do parâmetro");
        if (valor == null) {
            return this;
        }
        List<Parametro> novos = new ArrayList<>(parametros);
        novos.add(new Parametro(nome, String.valueOf(valor)));
        return new PortalRequest(method, path, List.copyOf(novos), headers, body);
    }

    /**
     * Define um header. Se o header já existir (sem diferenciar maiúsculas), o valor anterior é substituído.
     *
     * @param nome  nome do header, não vazio
     * @param valor valor do header, não nulo
     * @return nova requisição com o header (tipo não genérico)
     * @throws NullPointerException     se {@code nome} ou {@code valor} for nulo
     * @throws IllegalArgumentException se {@code nome} estiver vazio
     */
    public PortalRequest header(String nome, String valor) {
        requireName(nome, "nome do header");
        Objects.requireNonNull(valor, "valor do header " + nome);
        Map<String, String> novos = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        novos.putAll(headers);
        novos.put(nome, valor);
        return new PortalRequest(method, path, parametros, Collections.unmodifiableMap(novos), body);
    }

    /**
     * Define vários headers de uma vez. Cada entrada segue a regra de {@link #header(String, String)}.
     *
     * @param valores headers a definir; {@code null} equivale a nenhum header
     * @return nova requisição com os headers (tipo não genérico)
     * @throws NullPointerException     se algum nome ou valor de {@code valores} for nulo
     * @throws IllegalArgumentException se algum nome estiver vazio
     */
    public PortalRequest headers(Map<String, String> valores) {
        PortalRequest atual = this;
        if (valores != null) {
            for (Map.Entry<String, String> entrada : valores.entrySet()) {
                atual = atual.header(entrada.getKey(), entrada.getValue());
            }
        }
        return atual;
    }

    /**
     * Define o corpo da requisição como JSON. O {@code Content-Type} é definido por {@link PortalHttp}.
     *
     * @param json corpo JSON já serializado, não nulo
     * @return nova requisição com o corpo (tipo não genérico)
     * @throws NullPointerException se {@code json} for nulo
     */
    public PortalRequest json(String json) {
        Objects.requireNonNull(json, "corpo JSON");
        return new PortalRequest(method, path, parametros, headers, json);
    }

    /**
     * Caminho e query string codificados, prontos para serem anexados à base URL.
     *
     * @return {@code path} seguido de {@code ?} e dos parâmetros, se houver (tipo não genérico)
     */
    public String target() {
        if (parametros.isEmpty()) {
            return path;
        }
        StringBuilder alvo = new StringBuilder(path).append('?');
        for (int i = 0; i < parametros.size(); i++) {
            Parametro p = parametros.get(i);
            if (i > 0) {
                alvo.append('&');
            }
            alvo.append(codificar(p.nome())).append('=').append(codificar(p.valor()));
        }
        return alvo.toString();
    }

    /**
     * Método HTTP da requisição.
     *
     * @return método, não nulo (tipo não genérico)
     */
    public HttpMethod method() {
        return method;
    }

    /**
     * Caminho sem a query string.
     *
     * @return caminho começando com {@code /} (tipo não genérico)
     */
    public String path() {
        return path;
    }

    /**
     * Headers definidos explicitamente, sem os padrões de {@link PortalHttp}. Não modificável.
     *
     * @return mapa de headers com busca sem diferenciar maiúsculas (tipo genérico {@code Map<String, String>})
     */
    public Map<String, String> headers() {
        return headers;
    }

    /**
     * Corpo JSON da requisição.
     *
     * @return {@link Optional} com o corpo, vazio quando a requisição não tem corpo (tipo genérico {@code Optional<String>})
     */
    public Optional<String> body() {
        return Optional.ofNullable(body);
    }

    /**
     * Resumo seguro para log: método e caminho, sem query string, headers (que contêm tokens) ou corpo.
     *
     * @return texto no formato {@code PortalRequest[method=..., path=...]} (tipo não genérico)
     */
    @Override
    public String toString() {
        return "PortalRequest[method=" + method + ", path=" + path + "]";
    }

    /**
     * Valida um nome de parâmetro ou de header: não nulo e não vazio.
     *
     * @param nome      nome a validar
     * @param descricao descrição usada nas mensagens de erro (por exemplo, "nome do header")
     * @throws NullPointerException     se {@code nome} for nulo
     * @throws IllegalArgumentException se {@code nome} for vazio ou só tiver espaços
     */
    private static void requireName(String nome, String descricao) {
        Objects.requireNonNull(nome, descricao);
        if (nome.isBlank()) {
            throw new IllegalArgumentException(descricao + " não pode ser vazio.");
        }
    }

    /**
     * Codifica um valor para uso em query string: UTF-8, com espaço como {@code %20} (e não {@code +}).
     *
     * @param valor texto a codificar, não nulo
     * @return valor codificado (tipo não genérico)
     */
    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Par nome/valor de um parâmetro de query string. */
    private record Parametro(String nome, String valor) {
    }
}
