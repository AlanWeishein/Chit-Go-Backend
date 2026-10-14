package com.chitgo.auditoria.dominio;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Quién hizo qué, cuándo, y los valores anterior y nuevo (RF-36). */
@Entity
@Table(name = "registro_auditoria")
public class RegistroAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long localId;

    private Long usuarioId;

    private String accion;

    private String entidad;

    private Long entidadId;

    @JdbcTypeCode(SqlTypes.JSON)
    private String valorAnterior;

    @JdbcTypeCode(SqlTypes.JSON)
    private String valorNuevo;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant creadoEn;

    protected RegistroAuditoria() {
    }

    public RegistroAuditoria(Long localId, Long usuarioId, String accion, String entidad, Long entidadId, String valorAnterior, String valorNuevo) {
        this.localId = localId;
        this.usuarioId = usuarioId;
        this.accion = accion;
        this.entidad = entidad;
        this.entidadId = entidadId;
        this.valorAnterior = valorAnterior;
        this.valorNuevo = valorNuevo;
    }

    public Long getId() {
        return id;
    }

    public Long getLocalId() {
        return localId;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getAccion() {
        return accion;
    }

    public String getEntidad() {
        return entidad;
    }

    public Long getEntidadId() {
        return entidadId;
    }

    public String getValorAnterior() {
        return valorAnterior;
    }

    public String getValorNuevo() {
        return valorNuevo;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
