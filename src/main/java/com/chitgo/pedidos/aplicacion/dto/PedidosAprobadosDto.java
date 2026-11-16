package com.chitgo.pedidos.aplicacion.dto;

import java.util.List;

/** Los pedidos aprobados que ve Caja (P-36): los que Cocina no empezó, en el orden de la cola, y los que prepara. */
public record PedidosAprobadosDto(List<PedidoDeCocinaDto> sinEmpezar, List<PedidoDeCocinaDto> enPreparacion) {
}
