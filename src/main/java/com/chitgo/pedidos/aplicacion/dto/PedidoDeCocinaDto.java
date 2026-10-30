package com.chitgo.pedidos.aplicacion.dto;

import java.time.Instant;
import java.util.List;

public record PedidoDeCocinaDto(Long id, String mesa, String estado, boolean prioritario, Instant aprobadoEn,
                                Instant enPreparacionEn, Instant listoEn, List<ItemDeCocinaDto> items) {
}
