package com.chitgo.usuarios.dominio.puertos;

import java.time.Instant;
import java.util.Optional;

/**
 * Quién atiende una mesa ahora: asignado a la mesa, con un turno que cubre este momento (también el de ayer que cruza
 * la medianoche) y sin una ausencia el día en que empezó ese turno.
 */
public interface MozoDeTurno {

    /** El id del empleado, o vacío si no hay ninguno de turno. */
    Optional<Long> buscarParaMesa(Long mesaId, Instant ahora);
}
