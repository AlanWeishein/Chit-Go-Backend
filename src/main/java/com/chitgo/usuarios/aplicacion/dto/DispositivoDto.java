package com.chitgo.usuarios.aplicacion.dto;

import java.time.Instant;

/**
 * Respuesta de POST /auth/mozo/login y de GET /auth/dispositivo: el mozo que habilitó este dispositivo, para que
 * el front vaya directo a su PIN, y hasta cuándo puede entrar sin la contraseña.
 */
public record DispositivoDto(Long id, String nombre, Instant venceEn) {
}
