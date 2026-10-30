package com.chitgo.usuarios.dominio.puertos;

import java.time.Instant;
import java.util.Optional;

/** Quién atiende una mesa ahora: asignado a la mesa, con turno hoy y sin una ausencia que lo cubra. */
public interface MozoDeTurno {

    /** El id del empleado, o vacío si no hay ninguno de turno. */
    Optional<Long> buscarParaMesa(Long mesaId, Instant ahora);
}
