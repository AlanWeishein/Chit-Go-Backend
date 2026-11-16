package com.chitgo.usuarios.aplicacion.dto;

import java.time.Instant;

/**
 * Respuesta de GET /auth/sesion.
 *
 * @param local NULL para el Gerente/Dueño
 */
public record SesionDto(Long id, String nombre, String rol, LocalDeSesionDto local, boolean debeCambiarCredencial,
        Instant expiraEn) {

    public record LocalDeSesionDto(Long id, String nombre) {
    }
}
