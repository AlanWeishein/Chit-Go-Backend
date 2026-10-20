package com.chitgo.pedidos.aplicacion.dto;

import java.math.BigDecimal;
import java.util.List;

/** El estado completo y actual de la cuenta y sus pedidos, para reconectar sin repetir eventos (RNF-08). */
public record EstadoDeSesionDto(boolean esIniciador, String cuentaEstado, BigDecimal total, List<PedidoDto> pedidos) {
}
