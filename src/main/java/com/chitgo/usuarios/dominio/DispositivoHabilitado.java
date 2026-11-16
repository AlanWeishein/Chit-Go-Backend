package com.chitgo.usuarios.dominio;

import java.time.Instant;

/**
 * Lo que dice la cookie de dispositivo: qué mozo entró con usuario y contraseña en este dispositivo, cuándo,
 * y hasta cuándo puede seguir entrando solo con el PIN (RF-13). Cuánto dura sale de
 * {@code chitgo.reglas.vigencia-del-dispositivo}: 7 días por defecto (06/10 y 07/10).
 */
public record DispositivoHabilitado(Long usuarioId, Instant habilitadoEn, Instant venceEn) {
}
