package com.chitgo.usuarios.dominio;

import java.time.Duration;
import java.time.Instant;

/**
 * Lo que dice la cookie de dispositivo: qué mozo entró con usuario y contraseña en este dispositivo, cuándo,
 * y hasta cuándo puede seguir entrando solo con el PIN (RF-13).
 */
public record DispositivoHabilitado(Long usuarioId, Instant habilitadoEn, Instant venceEn) {

    /** Cada 7 días el mozo vuelve a entrar con usuario y contraseña (06/10 y 07/10). */
    public static final Duration VIGENCIA = Duration.ofDays(7);
}
