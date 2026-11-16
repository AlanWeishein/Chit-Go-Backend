package com.chitgo.usuarios.dominio.puertos;

import java.time.Instant;
import java.util.Set;

/**
 * Qué locales tienen un turno que empieza en este minuto, en la hora del local. Lo usa la alerta de mesa sin mozo
 * del inicio de los turnos (CP-07-05): solo mira las mesas de esos locales.
 */
public interface TurnosQueEmpiezan {

    /** Los ids de los locales con un turno de un empleado activo que empieza en el minuto de {@code ahora}. */
    Set<Long> localesConTurnoQueEmpieza(Instant ahora);
}
