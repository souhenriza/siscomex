package com.souhenriza.duimp;

import com.souhenriza.shared.ErroNegocio;
import com.souhenriza.shared.PortalException;

import java.io.Serial;
import java.util.List;

/**
 * Falha em uma consulta à API da DUIMP: resposta de erro do portal ou corpo que não pôde ser lido.
 */
public class DuimpException extends PortalException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DuimpException(String message) {
        super(message);
    }

    public DuimpException(String message, int status, String codigoInterno, List<ErroNegocio> erros) {
        super(message, status, codigoInterno, erros);
    }

    public DuimpException(String message, int status, String codigoInterno, List<ErroNegocio> erros,
                          Throwable cause) {
        super(message, status, codigoInterno, erros, cause);
    }
}
