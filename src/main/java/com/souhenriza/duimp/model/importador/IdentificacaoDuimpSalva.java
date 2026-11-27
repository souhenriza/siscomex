package com.souhenriza.duimp.model.importador;

/**
 * Identificação da DUIMP devolvida pelo portal ao salvar a capa.
 *
 * @param numero número da DUIMP
 * @param versao versão criada
 */
public record IdentificacaoDuimpSalva(String numero, Integer versao) {
}
