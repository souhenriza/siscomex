package com.souhenriza.autenticacao;

import com.souhenriza.autenticacao.erro.ExcecaoSiscomex;
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

class PerfilAcessoTest {

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
    void tabelaDePerfisConfereComDocumentacao(PerfilAcesso perfil, boolean eCpf, boolean eCnpj) {
        assertAll(
                () -> assertEquals(eCpf, perfil.aceitaECpf(), "e-CPF"),
                () -> assertEquals(eCnpj, perfil.aceitaECnpj(), "e-CNPJ")
        );
    }

    @Test
    @DisplayName("a documentação lista 14 perfis, nem mais nem menos")
    void quantidadeDePerfis() {
        assertEquals(14, PerfilAcesso.values().length);
    }

    @ParameterizedTest(name = "valor do portal \"{0}\" é aceito")
    @ValueSource(strings = {"IMPEXP", "TERCEIROS", "TRANSPEST"})
    @DisplayName("código do portal é aceito e resolvido para o perfil")
    void codigoValidoEhResolvido(String codigo) {
        assertEquals(codigo, PerfilAcesso.de(codigo).name());
    }

    @Test
    @DisplayName("espaços e caixa diferente são normalizados antes de validar")
    void normalizaEspacosECaixa() {
        assertEquals(PerfilAcesso.IMPEXP, PerfilAcesso.de("  impexp "));
    }

    @ParameterizedTest(name = "valor \"{0}\" é rejeitado com PUCX-ER0102")
    @ValueSource(strings = {"ACESSOPUBLICO", "IMPEXP2", "TRANS PORT", "CPF"})
    @DisplayName("perfil fora da lista é rejeitado localmente com PUCX-ER0102")
    void perfilInexistenteEhRejeitado(String valor) {
        ExcecaoSiscomex e = assertThrows(ExcecaoSiscomex.class, () -> PerfilAcesso.de(valor));
        assertTrue(e.getMessage().startsWith("PUCX-ER0102"), e.getMessage());
    }

    @ParameterizedTest(name = "valor {0} é rejeitado com PUCX-ER0101")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("perfil ausente, vazio ou em branco é rejeitado com PUCX-ER0101")
    void perfilAusenteEhRejeitado(String valor) {
        ExcecaoSiscomex e = assertThrows(ExcecaoSiscomex.class, () -> PerfilAcesso.de(valor));
        assertTrue(e.getMessage().startsWith("PUCX-ER0101"), e.getMessage());
    }
}
