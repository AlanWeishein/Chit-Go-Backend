package com.chitgo.pedidos.aplicacion.dto;

import java.math.BigDecimal;

/** Un renglón del ticket: el mismo plato al mismo precio, sumadas las cantidades de todos los pedidos. */
public record ItemDeTicketDto(String plato, int cantidad, BigDecimal precioUnitario, BigDecimal subtotal) {
}
