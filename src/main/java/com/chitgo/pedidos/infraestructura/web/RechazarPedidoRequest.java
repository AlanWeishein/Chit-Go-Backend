package com.chitgo.pedidos.infraestructura.web;

import jakarta.validation.constraints.NotNull;

import com.chitgo.pedidos.dominio.MotivoRechazo;

/** El cuerpo de POST /caja/pedidos/{id}/rechazar. Sin version: el back la toma de la base (21/10). */
public record RechazarPedidoRequest(@NotNull MotivoRechazo motivo) {
}
