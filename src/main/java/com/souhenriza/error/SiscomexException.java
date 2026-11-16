package com.souhenriza.error;

import com.souhenriza.auth.dto.AuthResponseError;

import java.io.Serial;

public class SiscomexEception extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final AuthResponseError responseError;

    public SiscomexEception(AuthResponseError responseError, String message) {
        super(message);
        this.responseError = responseError;
    }

    
}
