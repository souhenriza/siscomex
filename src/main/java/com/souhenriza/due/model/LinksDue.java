package com.souhenriza.due.model;

import com.souhenriza.shared.Campos;
import tools.jackson.databind.JsonNode;

public record LinksDue(
        String href,
        String method,
        String rel,
        String title,
        String type
) {

    public static LinksDue de(JsonNode raw) {
        return new LinksDue(Campos.texto(raw, "href"), Campos.texto(raw, "method"),
                Campos.texto(raw, "rel"), Campos.texto(raw, "title"), Campos.texto(raw, "type"));
    }
}
