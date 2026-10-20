package com.chitgo.pedidos.aplicacion.dto;

/** Lo que Cocina necesita de cada ítem: plato, cantidad y observación. Sin precios. */
public record ItemDeCocinaDto(String plato, int cantidad, String observacion) {
}
