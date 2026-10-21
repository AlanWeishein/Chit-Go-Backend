package com.chitgo.pedidos.aplicacion.dto;

/** nueva = false cuando el celular ya tenía una sesión vigente en esa cuenta y se le devuelve la misma. */
public record ResultadoDeSesion(SesionDeMesaDto sesion, boolean nueva) {
}
