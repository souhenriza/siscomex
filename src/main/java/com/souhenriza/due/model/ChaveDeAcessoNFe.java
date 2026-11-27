package com.souhenriza.due.model;

import com.souhenriza.due.DueException;

public record ChaveDeAcessoNFe(
        String chaveDeAcessoNFe
) {

    public ChaveDeAcessoNFe {
        validar(chaveDeAcessoNFe);
    }

    /**
     * Já está sendo considerado CNPJ alfanuméricos.
     *
     * @throws DueException quando há erro de validação.
     */
    private void validar(String chaveDeAcesso) throws DueException {
       if (chaveDeAcesso == null || chaveDeAcesso.length() != 44) {
           throw new DueException("A chave de acesso não pode ser nula ou ter quantidade de caracteres menor que 44 dígitos. ");
       }

        char lastChar = chaveDeAcesso.charAt(43);
       if (lastChar < '0' || lastChar > '9') {
           throw new DueException("O dígito verificador não pode ser menor que 0 ou maior que 9. ");
       }

       int lastNum = lastChar - '0';

       if (calculateDv(chaveDeAcesso) != lastNum) {
           throw new DueException("Dígito verificador da chave informada é inválido. ");
       };

     }

    private int calculateDv(String chaveDeAcessoNFe) {
        int total = 0;
        int peso = 2;

        for (int i = chaveDeAcessoNFe.length() - 2; i >= 0 ; i--) {
            int valorAscii = (int) chaveDeAcessoNFe.charAt(i) - '0';
            total +=  valorAscii * peso;
            peso ++;

            if (peso > 9) {
                peso = 2;
            }
        }

        int rest = total % 11;

        return (rest <= 1 ? 0 : 11 - rest);
    }
}
