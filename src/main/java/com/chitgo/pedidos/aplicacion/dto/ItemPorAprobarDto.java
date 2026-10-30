package com.chitgo.pedidos.aplicacion.dto;

/** Lo que Caja mira de cada ítem: el plato, si sigue disponible, cantidad y observación. Sin precios. */
public record ItemPorAprobarDto(Long platoId, String plato, int cantidad, String observacion, boolean disponible) {
}
