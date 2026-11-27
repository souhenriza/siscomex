package com.souhenriza.api;

import com.souhenriza.autenticacao.api.ClienteApi;
import com.souhenriza.autenticacao.api.RespostaSiscomex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogRedactionTest {

    @Test
    @DisplayName("Oculta o CNPJ do importador no caminho usado em log")
    void redactsImporterNiInPath() {
        assertEquals("/duimp-api/api/ext/duimp/chaves-acesso/importadores/***?data-inicio=2026-01-01&limit=10",
                ClienteApi.ocultarIdentificador("/duimp-api/api/ext/duimp/chaves-acesso/importadores/12345678000199"
                        + "?data-inicio=2026-01-01&limit=10"));
    }

    @Test
    @DisplayName("Oculta o CPF do importador quando a consulta é feita por CPF")
    void redactsCpfInPath() {
        assertEquals("/x/chaves-acesso/importadores/***",
                ClienteApi.ocultarIdentificador("/x/chaves-acesso/importadores/12345678901"));
    }

    @Test
    @DisplayName("Log não inclui a query string (chave de NF-e, CNPJ de beneficiário, número da DUIMP)")
    void logOmitsQueryString() {
        assertEquals("/due/api/ext/due",
                ClienteApi.paraLog("/due/api/ext/due?nota-fiscal=35200614200166000187550010000000011000000010"));
        assertEquals("/duimp-api/api/ext/duimp/chaves-acesso/importadores/***",
                ClienteApi.paraLog("/duimp-api/api/ext/duimp/chaves-acesso/importadores/12345678000199?data-inicio=2026-01-01"));
        assertEquals("/x/y", ClienteApi.paraLog("/x/y"));
    }

    @Test
    @DisplayName("Caminhos sem NI do importador permanecem inalterados")
    void keepsOtherPaths() {
        String path = "/duimp-api/api/ext/duimp/21BR00000012345/1/itens?inicial=1&tamanho=100";
        assertEquals(path, ClienteApi.ocultarIdentificador(path));
    }

    @Test
    @DisplayName("toString da resposta não expõe JWT nem CSRF")
    void responseToStringMasksSessionHeaders() {
        RespostaSiscomex response = new RespostaSiscomex(200, "{}", Map.of(
                "Set-Token", "eyJsecretjwt",
                "x-csrf-token", "csrf-secret",
                "Content-Type", "application/json"));

        String text = response.toString();

        assertFalse(text.contains("eyJsecretjwt"), text);
        assertFalse(text.contains("csrf-secret"), text);
        assertTrue(text.contains("application/json"), text);
        assertTrue(text.contains("status=200"), text);
        // os valores continuam acessíveis para o código que precisa deles
        assertEquals("eyJsecretjwt", response.cabecalho("Set-Token").orElseThrow());
    }
}
