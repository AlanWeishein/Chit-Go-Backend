package com.chitgo.usuarios.infraestructura.persistencia;

import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Repository;
import com.chitgo.usuarios.dominio.puertos.AsignacionesDeMozo;

@Repository
class AsignacionesDeMozoAdapter implements AsignacionesDeMozo {
    private final EmpleadoJpaRepository jpa;

    AsignacionesDeMozoAdapter(EmpleadoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Set<Long> mesasDe(Long usuarioId, Long localId) {
        return jpa.mesasDe(usuarioId, localId);
    }

    @Override
    public Optional<Long> usuarioDelEmpleado(Long empleadoId, Long localId) {
        return jpa.usuarioDelEmpleado(empleadoId, localId);
    }
}
