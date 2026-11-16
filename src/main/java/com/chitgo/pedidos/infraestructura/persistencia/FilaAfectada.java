package com.chitgo.pedidos.infraestructura.persistencia;

import com.chitgo.pedidos.dominio.EstadoPedido;

/** Una fila de la consulta de los pedidos afectados (HU-09): un ítem con los datos de su pedido y de su plato. */
record FilaAfectada(Long pedidoId, String mesa, EstadoPedido estado, Long platoId, String plato, short cantidad) {
}
