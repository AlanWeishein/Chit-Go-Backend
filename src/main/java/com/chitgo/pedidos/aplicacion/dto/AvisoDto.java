package com.chitgo.pedidos.aplicacion.dto;

import java.time.Instant;

public record AvisoDto(Long id, Long mesaId, String mesa, String tipo, Instant creadoEn,
                       Long atendidoPorUsuarioId, Instant atendidoEn) {
}
