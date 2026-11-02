package com.chitgo.pedidos.dominio.puertos;

import java.util.Optional;
import java.util.List;

import com.chitgo.pedidos.dominio.Mesa;

public interface MesaRepository {

    Optional<Mesa> buscarPorTokenQr(String tokenQr);

    /**
     * Igual, pero bloquea la fila hasta el fin de la transacción: dos escaneos a la vez de una mesa libre se
     * hacen de a uno y se abre una sola cuenta.
     */
    Optional<Mesa> buscarPorTokenQrParaAbrirCuenta(String tokenQr);

    List<Mesa> listarDelLocal(Long localId);
}
