package com.chitgo.pedidos.aplicacion.dto;

/** Lo que se sabe de la mesa antes de entrar: si está disponible y si ya tiene una cuenta abierta. */
public record EstadoDeMesaDto(String numero, boolean disponible, boolean cuentaAbierta) {
}
