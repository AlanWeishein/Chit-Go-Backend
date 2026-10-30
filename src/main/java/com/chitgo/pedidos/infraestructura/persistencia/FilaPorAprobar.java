package com.chitgo.pedidos.infraestructura.persistencia;

import java.time.Instant;

/** Una fila de la consulta de la cola de Caja: un ítem con los datos de su pedido y de su plato. */
record FilaPorAprobar(Long pedidoId, String mesa, Instant enviadoEn, Long platoId, String plato, short cantidad,
                      String observacion, boolean disponible) {
}
