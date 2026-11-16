package com.souhenriza.auth.dto;

public record AuthResponseError(
        int status,
        String message,
        String tag,
        String code
) {
}
