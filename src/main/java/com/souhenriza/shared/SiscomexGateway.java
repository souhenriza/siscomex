package com.souhenriza.shared;

import com.souhenriza.autenticacao.api.RequisicaoSiscomex;
import com.souhenriza.autenticacao.api.RespostaSiscomex;

/**
 * Envio de requisições ao portal. {@code Siscomex} atende a esta interface pelo método {@code send}; testes
 * podem usar uma implementação própria, sem rede.
 */
@FunctionalInterface
public interface SiscomexGateway {

    RespostaSiscomex send(RequisicaoSiscomex request);
}
