package com.chitgo.usuarios.aplicacion.dto;

import java.time.Instant;

/**
 * Respuesta de GET /auth/sesion.
 *
 * @param local                NULL para el Gerente/Dueño
 * @param contrasenaProvisoria el mozo todavía tiene la contraseña del alta: con debeCambiarCredencial, el front
 *                             le muestra P-32b (decidido el 20/10, punto 10). Después de un blanqueo de PIN va
 *                             en FALSE. Sale de la base, no del token.
 */
public record SesionDto(Long id, String nombre, String rol, LocalDeSesionDto local, boolean debeCambiarCredencial,
        boolean contrasenaProvisoria, Instant expiraEn) {

    public record LocalDeSesionDto(Long id, String nombre) {
    }
}
