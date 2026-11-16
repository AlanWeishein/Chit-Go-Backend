package com.chitgo.usuarios.dominio.puertos;

import java.util.Optional;
import java.util.Set;

/** Los ids de cuenta.mozoId son de empleado; la sesión y las suscripciones usan ids de usuario. */
public interface AsignacionesDeMozo {
    Set<Long> mesasDe(Long usuarioId, Long localId);

    Optional<Long> usuarioDelEmpleado(Long empleadoId, Long localId);
}
