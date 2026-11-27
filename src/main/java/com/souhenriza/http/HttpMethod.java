package com.souhenriza.http;

/**
 * Métodos HTTP usados pelos serviços do Portal Único.
 * <p>
 * O enum restringe o conjunto de verbos aceitos, o que evita enviar métodos desconhecidos ao portal.
 */
public enum HttpMethod {

    /** Consulta sem alteração de estado. */
    GET,

    /** Criação de recurso ou envio de dados. */
    POST,

    /** Substituição de recurso. */
    PUT,

    /** Remoção de recurso. */
    DELETE
}
