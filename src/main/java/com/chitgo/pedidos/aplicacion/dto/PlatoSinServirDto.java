package com.chitgo.pedidos.aplicacion.dto;

/** Un plato de un pedido sin servir, tal como se lo muestra a Caja en la confirmación del cierre. Sin precios. */
public record PlatoSinServirDto(String plato, int cantidad) {
}
