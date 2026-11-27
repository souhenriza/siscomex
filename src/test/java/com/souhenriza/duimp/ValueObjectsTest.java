package com.souhenriza.duimp;

import com.souhenriza.duimp.model.importador.IntervaloItens;
import com.souhenriza.duimp.model.importador.NumeroDuimp;
import com.souhenriza.duimp.model.importador.NumeroItem;
import com.souhenriza.duimp.model.importador.VersaoDuimp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValueObjectsTest {

    @ParameterizedTest
    @ValueSource(strings = {"21BR00000012345", "19BR00000004677", "20BR00000012345"})
    void aceitaNumeroNoFormatoDaDuimp(String valor) {
        assertEquals(valor, NumeroDuimp.of(valor).valor());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "21BR0000001234",          // 14 caracteres
            "21BR000000123456",        // 16 caracteres
            "21XX00000012345",         // sigla diferente de BR
            "21BR0000001234A",         // letra no lugar de dígito
            "../21BR00000012345",      // tentativa de sair do caminho
            "21BR00000012345/1",       // barra no número
            "21BR00000012345 "         // espaço no fim
    })
    void recusaNumeroForaDoFormato(String valor) {
        assertThrows(DuimpException.class, () -> NumeroDuimp.of(valor));
    }

    @Test
    void versaoEntreZeroeNoventaENoveMil() {
        assertDoesNotThrow(() -> VersaoDuimp.of(0));
        assertDoesNotThrow(() -> VersaoDuimp.of(9999));
        assertThrows(DuimpException.class, () -> VersaoDuimp.of(-1));
        assertThrows(DuimpException.class, () -> VersaoDuimp.of(10000));
    }

    @Test
    void numeroDeItemEntreUmECincoDigitos() {
        assertDoesNotThrow(() -> NumeroItem.of(1));
        assertDoesNotThrow(() -> NumeroItem.of(99_999));
        assertThrows(DuimpException.class, () -> NumeroItem.of(0));
        assertThrows(DuimpException.class, () -> NumeroItem.of(100_000));
    }

    @Test
    void intervaloDeItensRespeitaOLimiteDeCem() {
        assertEquals(new IntervaloItens(1, 100), IntervaloItens.padrao());
        assertDoesNotThrow(() -> new IntervaloItens(5, 100));
        assertThrows(DuimpException.class, () -> new IntervaloItens(0, 10));
        assertThrows(DuimpException.class, () -> new IntervaloItens(1, 0));
        assertThrows(DuimpException.class, () -> new IntervaloItens(1, 101));
    }
}
