package com.chitgo.usuarios.infraestructura.persistencia;

import java.util.Collection;
import java.util.Set;

import org.springframework.stereotype.Repository;

import com.chitgo.usuarios.dominio.puertos.MesasDelLocal;

/** Lee la tabla {@code mesa} con JPQL, sin importar la clase {@code Mesa} de {@code pedidos}. */
@Repository
class MesasDelLocalAdapter implements MesasDelLocal {

    private final EmpleadoJpaRepository jpa;

    MesasDelLocalAdapter(EmpleadoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Set<Long> deEsteLocal(Collection<Long> mesaIds, Long localId) {
        if (mesaIds.isEmpty()) {
            return Set.of();
        }
        return jpa.mesasDelLocal(mesaIds, localId);
    }
}
