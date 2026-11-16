package com.souhenriza.auth.error;

import com.souhenriza.auth.dto.AuthResponseError;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * Monta as exceções de falha de comunicação e de erro devolvido pelo portal, para uso por qualquer
 * chamada ao portal (autenticação e serviços).
 */
public final class PortalFailures {

    private PortalFailures() {
    }

    /** Erro devolvido pelo portal com código conhecido. */
    public static SiscomexException portalError(PortalErrorCode code, AuthResponseError error, Duration retryAfter) {
        StringBuilder message = new StringBuilder("[").append(code.code()).append("] ").append(code.description());

        if (error != null && error.message() != null && !error.message().isBlank()) {
            message.append(" Mensagem do portal: ").append(error.message());
        }

        return new SiscomexException(message.toString(), error, code, retryAfter);
    }

    /** Falha de transporte: TLS, timeout ou conexão. Timeout e conexão são retentáveis. */
    public static SiscomexException communicationFailure(IOException e) {
        if (causedBy(e, SSLException.class)) {
            return new SiscomexException("Falha TLS ao comunicar com o portal. O certificado do servidor pode estar fora do TrustStore ou o certificado do cliente pode não ser válido.", e);
        }

        if (e instanceof HttpTimeoutException) {
            return new SiscomexException("Tempo esgotado ao comunicar com o portal.", e, true);
        }

        return new SiscomexException("Falha na comunicação com o portal.", e, true);
    }

    private static boolean causedBy(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }
}
