package com.souhenriza.duimp;

import com.souhenriza.auth.api.SiscomexRequest;
import com.souhenriza.auth.api.SiscomexResponse;

/**
 * Envio de requisições ao portal. {@code Siscomex} atende a esta interface pelo método {@code send}; testes
 * podem usar uma implementação própria, sem rede.
 */
@FunctionalInterface
public interface SiscomexGateway {

    SiscomexResponse send(SiscomexRequest request);
}
