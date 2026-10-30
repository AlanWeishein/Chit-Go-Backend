package com.chitgo.usuarios.dominio;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

/** Mesa asignada a un mozo. La mesa es de otro módulo, así que va como id. */
@Entity
@Table(name = "mozo_mesa")
public class MozoMesa {

    @EmbeddedId
    private MozoMesaId id;

    private Long localId;

    @MapsId("empleadoId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Empleado empleado;

    protected MozoMesa() {
    }

    public MozoMesa(Long localId, Empleado empleado, Long mesaId) {
        this.id = new MozoMesaId(empleado.getId(), mesaId);
        this.localId = localId;
        this.empleado = empleado;
        empleado.asignarMesa(this);
    }

    public MozoMesaId getId() {
        return id;
    }

    public Long getLocalId() {
        return localId;
    }

    public Empleado getEmpleado() {
        return empleado;
    }

    public Long getMesaId() {
        return id.getMesaId();
    }
}
