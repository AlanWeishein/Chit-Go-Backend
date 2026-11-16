package com.chitgo.pedidos.aplicacion.dto;

import java.util.List;

public record MesaDeMozoDto(Long id, String numero, boolean inhabilitada, boolean asignada,
                           Long cuentaId, String estado, List<PedidoDto> pedidos) {
}
