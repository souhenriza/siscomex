package com.souhenriza.auth;

import com.souhenriza.auth.error.SiscomexException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleTypeTest {

    @DisplayName("tabela de perfis confere com a documentação (e-CPF / e-CNPJ)")
    @ParameterizedTest(name = "{0}: e-CPF={1}, e-CNPJ={2}")
    @CsvSource({
            "AGECARGA,     true,  false",
            "AGEREMESS,    true,  true",
            "AJUDESPAC,    true,  false",
            "CONTATOOEA,   true,  false",
            "DEPOSIT,      true,  true",
            "EMPRCOMEL,    true,  true",
            "HABILITAD,    true,  false",
            "IMPEXP,       true,  false",
            "INSTFINANC,   true,  false",
            "OPERPORT,     true,  true",
            "RESPLEGAL,    true,  false",
            "TRANSPEST,    true,  false",
            "TRANSPORT,    true,  true",
            "TERCEIROS,    false, true"
    })
    void tabelaDePerfisConfereComDocumentacao(RoleType perfil, boolean eCpf, boolean eCnpj) {
        assertAll(
                () -> assertEquals(eCpf, perfil.allowECpf(), "e-CPF"),
                () -> assertEquals(eCnpj, perfil.allowECnpj(), "e-CNPJ")
        );
    }

    @Test
    @DisplayName("a documentação lista 14 perfis, nem mais nem menos")
    void quantidadeDePerfis() {
        assertEquals(14, RoleType.values().length);
    }

    @ParameterizedTest(name = "valor do portal \"{0}\" é aceito")
    @ValueSource(strings = {"IMPEXP", "TERCEIROS", "TRANSPEST"})
    @DisplayName("código do portal é aceito e resolvido para o perfil")
    void codigoValidoEhResolvido(String codigo) {
        assertEquals(codigo, RoleType.from(codigo).name());
    }

    @Test
    @DisplayName("espaços e caixa diferente são normalizados antes de validar")
    void normalizaEspacosECaixa() {
        assertEquals(RoleType.IMPEXP, RoleType.from("  impexp "));
    }

    @ParameterizedTest(name = "valor \"{0}\" é rejeitado com PUCX-ER0102")
    @ValueSource(strings = {"ACESSOPUBLICO", "IMPEXP2", "TRANS PORT", "CPF"})
    @DisplayName("perfil fora da lista é rejeitado localmente com PUCX-ER0102")
    void perfilInexistenteEhRejeitado(String valor) {
        SiscomexException e = assertThrows(SiscomexException.class, () -> RoleType.from(valor));
        assertTrue(e.getMessage().startsWith("PUCX-ER0102"), e.getMessage());
    }

    @ParameterizedTest(name = "valor {0} é rejeitado com PUCX-ER0101")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("perfil ausente, vazio ou em branco é rejeitado com PUCX-ER0101")
    void perfilAusenteEhRejeitado(String valor) {
        SiscomexException e = assertThrows(SiscomexException.class, () -> RoleType.from(valor));
        assertTrue(e.getMessage().startsWith("PUCX-ER0101"), e.getMessage());
    }
}
