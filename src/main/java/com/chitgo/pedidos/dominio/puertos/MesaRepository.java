package com.chitgo.pedidos.dominio.puertos;

import java.util.Optional;
import java.util.List;

import com.chitgo.pedidos.dominio.Mesa;
import com.chitgo.compartido.dominio.PaginaDe;

public interface MesaRepository {

    Optional<Mesa> buscarPorTokenQr(String tokenQr);

    /**
     * Igual, pero bloquea la fila hasta el fin de la transacción: dos escaneos a la vez de una mesa libre se
     * hacen de a uno y se abre una sola cuenta.
     */
    Optional<Mesa> buscarPorTokenQrParaAbrirCuenta(String tokenQr);

    List<Mesa> listarDelLocal(Long localId);

    Optional<Mesa> buscarPorId(Long id, Long localId);

    /** El mismo lock que el escaneo: no se inhabilita ni se da de baja mientras se abre una cuenta. */
    Optional<Mesa> buscarPorIdParaGestionar(Long id, Long localId);

    PaginaDe<Mesa> pagina(Long localId, int page, int size);

    Mesa guardar(Mesa mesa);
}
