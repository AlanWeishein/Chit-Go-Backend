package com.chitgo.usuarios.dominio;

import java.time.Instant;

/** Un token que pasó la validación, con su vencimiento (para el expiraEn de /auth/sesion). */
public record TokenLeido(DatosDeSesion datos, Instant expiraEn) {
}
