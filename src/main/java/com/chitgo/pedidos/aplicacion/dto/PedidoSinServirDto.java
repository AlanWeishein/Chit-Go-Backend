package com.chitgo.pedidos.aplicacion.dto;

import java.util.List;

/** Un pedido que el cierre de la cuenta va a cancelar (HU-10). */
public record PedidoSinServirDto(Long id, String estado, List<PlatoSinServirDto> platos) {
}
