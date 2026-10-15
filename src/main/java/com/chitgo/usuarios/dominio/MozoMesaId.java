package com.chitgo.usuarios.dominio;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Embeddable;

/** Clave de MozoMesa: el empleado y la mesa asignada. */
@Embeddable
public class MozoMesaId implements Serializable {

    private Long empleadoId;

    private Long mesaId;

    protected MozoMesaId() {
    }

    MozoMesaId(Long empleadoId, Long mesaId) {
        this.empleadoId = empleadoId;
        this.mesaId = mesaId;
    }

    public Long getEmpleadoId() {
        return empleadoId;
    }

    public Long getMesaId() {
        return mesaId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MozoMesaId otro)) {
            return false;
        }
        return Objects.equals(empleadoId, otro.empleadoId) && Objects.equals(mesaId, otro.mesaId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(empleadoId, mesaId);
    }
}
