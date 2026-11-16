package com.souhenriza.auth.error;

import com.souhenriza.auth.dto.AuthResponseError;

import java.io.Serial;
import java.time.Duration;
import java.util.Optional;

public class SiscomexException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final AuthResponseError responseError;
    private final PortalErrorCode portalErrorCode;
    private final Duration retryAfter;
    private final boolean retryable;

    public SiscomexException(String message, AuthResponseError responseError, PortalErrorCode portalErrorCode,
                             Duration retryAfter) {
        this(message, null, responseError, portalErrorCode, retryAfter, false);
    }

    public SiscomexException(String message, AuthResponseError responseError) {
        this(message, null, responseError, null, null, false);
    }

    public SiscomexException(String message) {
        this(message, null, null, null, null, false);
    }

    public SiscomexException(String message, Throwable cause) {
        this(message, cause, null, null, null, false);
    }

    public SiscomexException(String message, Throwable cause, boolean retryable) {
        this(message, cause, null, null, null, retryable);
    }

    private SiscomexException(String message, Throwable cause, AuthResponseError responseError,
                              PortalErrorCode portalErrorCode, Duration retryAfter, boolean retryable) {
        super(message, cause);
        this.responseError = responseError;
        this.portalErrorCode = portalErrorCode;
        this.retryAfter = retryAfter;
        this.retryable = retryable || retryAfter != null
                || (portalErrorCode != null && portalErrorCode.retryable());
    }

    public AuthResponseError getResponseError() {
        return responseError;
    }

    /** Código mapeado do portal, quando conhecido. */
    public Optional<PortalErrorCode> portalErrorCode() {
        return Optional.ofNullable(portalErrorCode);
    }

    /** Tempo a aguardar antes de tentar novamente, quando o portal ou a janela de parada o informa. */
    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }

    /** Indica se a operação pode ser repetida. */
    public boolean isRetryable() {
        return retryable;
    }
}
