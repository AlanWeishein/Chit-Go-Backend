package com.chitgo.pedidos.dominio.puertos;

import java.util.List;

/** Proyección pública mínima: no expone entidades, ítems ni datos de usuarios. */
public interface PedidosDePantalla {
    List<PedidoVisible> delLocal(Long localId);
    record PedidoVisible(Long id, String mesa, String estado) { }
}
