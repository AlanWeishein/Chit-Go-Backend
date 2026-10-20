package com.chitgo.pedidos.aplicacion.dto;

/** La sesión del celular en la mesa. El token es el que va después en el header X-Session-Token. */
public record SesionDeMesaDto(String tokenSesion, boolean esIniciador) {
}
