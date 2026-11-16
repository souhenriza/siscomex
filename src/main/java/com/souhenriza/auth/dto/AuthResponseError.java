package com.souhenriza.auth.dto;

public record AuthResponse(
        int status,
        String message,
        String tag,
        String 
) {
}
