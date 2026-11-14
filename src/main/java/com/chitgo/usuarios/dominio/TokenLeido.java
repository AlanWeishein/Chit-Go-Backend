package com.chitgo.usuarios.dominio;

import java.time.Instant;

/**
 * Un token que pasó la validación, con su vencimiento (para el expiraEn de /auth/sesion) y su emisión, en
 * segundos (para compararla con el "válido desde" del logout).
 */
public record TokenLeido(DatosDeSesion datos, Instant expiraEn, Instant emitidoEn) {
}
