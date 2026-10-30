package com.chitgo.pedidos.aplicacion.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Una cuenta por cobrar de P-09. {@code mesa} es el número que se muestra, no el id.
 *
 * @param total            lo que va hasta ahora, sin rechazados ni cancelados, con el precio de cada pedido
 * @param pedidosSinServir cuántos están en Pendiente, Aprobado, En preparación o Listo: los que el cierre cancelaría
 */
public record CuentaPorCobrarDto(Long id, String mesa, String estado, Instant abiertaEn, Instant solicitadaEn,
                                 BigDecimal total, int pedidosSinServir) {
}
