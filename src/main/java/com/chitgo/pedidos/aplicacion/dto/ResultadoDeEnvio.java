package com.chitgo.pedidos.aplicacion.dto;

/** creado = false cuando era un reintento y se devuelve el pedido que ya existía (RNF-09). */
public record ResultadoDeEnvio(PedidoDto pedido, boolean creado) {
}
