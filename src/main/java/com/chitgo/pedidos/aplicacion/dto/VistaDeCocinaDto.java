package com.chitgo.pedidos.aplicacion.dto;

import java.util.List;

/** Las tres columnas del KDS (P-11). La cola sale ya en el orden en que hay que preparar. */
public record VistaDeCocinaDto(List<PedidoDeCocinaDto> enCola, List<PedidoDeCocinaDto> enPreparacion,
                               List<PedidoDeCocinaDto> listos) {
}
