package com.chitgo.pedidos.aplicacion.dto;

import java.time.Instant;
import java.util.List;

/**
 * Un pedido de la cola de Caja (P-08). La espera y {@code demorado} salen del reloj del servidor (21/10).
 *
 * @param demorado más de 3 minutos sin resolverse (RF-16)
 */
public record PedidoPorAprobarDto(Long id, String mesa, Instant enviadoEn, long segundosDeEspera, boolean demorado,
                                  List<ItemPorAprobarDto> items) {
}
