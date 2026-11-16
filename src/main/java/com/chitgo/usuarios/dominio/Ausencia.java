package com.chitgo.usuarios.dominio;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

/** Vacaciones, franco o baja temporal, aplicada encima del Turno. */
@Entity
@Table(name = "ausencia")
public class Ausencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long localId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Empleado empleado;

    @Enumerated(EnumType.STRING)
    private TipoAusencia tipo;

    private LocalDate desde;

    private LocalDate hasta;

    private Long creadaPorUsuarioId;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant creadoEn;

    protected Ausencia() {
    }

    public Ausencia(Long localId, Empleado empleado, TipoAusencia tipo, LocalDate desde, LocalDate hasta, Long creadaPorUsuarioId) {
        this.localId = localId;
        this.empleado = empleado;
        this.tipo = tipo;
        this.desde = desde;
        this.hasta = hasta;
        this.creadaPorUsuarioId = creadaPorUsuarioId;
    }

    public Long getId() {
        return id;
    }

    public Long getLocalId() {
        return localId;
    }

    public Empleado getEmpleado() {
        return empleado;
    }

    public TipoAusencia getTipo() {
        return tipo;
    }

    public LocalDate getDesde() {
        return desde;
    }

    public LocalDate getHasta() {
        return hasta;
    }

    public Long getCreadaPorUsuarioId() {
        return creadaPorUsuarioId;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
