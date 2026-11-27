package com.souhenriza.due;

import com.souhenriza.shared.ErroNegocio;
import com.souhenriza.shared.PortalException;

import java.io.Serial;
import java.util.List;

/**
 * Falha em uma consulta à API da DUE: resposta de erro do portal ou corpo que não pôde ser lido.
 */
public class DueException extends PortalException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DueException(String message) {
        super(message);
    }

    public DueException(String message, int status, String codigoInterno, List<ErroNegocio> erros) {
        super(message, status, codigoInterno, erros);
    }

    public DueException(String message, int status, String codigoInterno, List<ErroNegocio> erros,
                        Throwable cause) {
        super(message, status, codigoInterno, erros, cause);
    }
}
