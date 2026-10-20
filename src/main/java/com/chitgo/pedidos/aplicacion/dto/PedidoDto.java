package com.chitgo.pedidos.aplicacion.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PedidoDto(Long id, String estado, String motivoRechazo, boolean prioridadManual, Instant enviadoEn,
                        BigDecimal total, List<ItemDto> items) {
}
