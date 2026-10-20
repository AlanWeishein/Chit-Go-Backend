package com.chitgo.pedidos.aplicacion.dto;

import java.math.BigDecimal;

public record ItemDto(Long platoId, String plato, int cantidad, BigDecimal precioUnitario, String observacion) {
}
