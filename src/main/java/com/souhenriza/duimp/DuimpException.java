package com.souhenriza.duimp;

import java.io.Serial;
import java.util.List;

/**
 * Falha em uma consulta à API da DUIMP: resposta de erro do portal ou corpo que não pôde ser lido.
 * Falhas de autenticação e de comunicação continuam sendo {@code SiscomexException}.
 */
public class DuimpException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int status;
    private final String codigoInterno;
    private final List<ErroNegocio> erros;

    public DuimpException(String message, int status, String codigoInterno, List<ErroNegocio> erros) {
        this(message, status, codigoInterno, erros, null);
    }

    public DuimpException(String message, int status, String codigoInterno, List<ErroNegocio> erros, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.codigoInterno = codigoInterno;
        this.erros = erros == null ? List.of() : List.copyOf(erros);
    }

    /** Código HTTP devolvido pelo portal. */
    public int status() {
        return status;
    }

    /** Código interno do portal, como {@code DIMP-ER0001}, quando informado. */
    public String codigoInterno() {
        return codigoInterno;
    }

    /** Erros de negócio detalhados, quando o portal os informa. */
    public List<ErroNegocio> erros() {
        return erros;
    }

    /** Recurso não encontrado (HTTP 404). */
    public boolean isNotFound() {
        return status == 404;
    }

    /** Erro de validação da camada de negócio (HTTP 422). */
    public boolean isBusinessError() {
        return status == 422;
    }

    /** Erro interno do portal (HTTP 5xx), que pode ser repetido mais tarde. */
    public boolean isRetryable() {
        return status >= 500;
    }
}
