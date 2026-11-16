package com.chitgo.pedidos.infraestructura.persistencia;

/** Una fila de la consulta de la alerta de los 3 minutos: el pedido con su local y su mesa. */
record FilaPendiente(Long pedidoId, Long localId, Long mesaId) {
}
