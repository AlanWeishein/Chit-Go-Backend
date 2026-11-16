package com.chitgo.usuarios.dominio;

import java.time.LocalTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Horario semanal recurrente de un empleado. */
@Entity
@Table(name = "turno")
public class Turno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long localId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Empleado empleado;

    @Enumerated(EnumType.STRING)
    private DiaSemana diaSemana;

    private LocalTime horaInicio;

    private LocalTime horaFin;

    protected Turno() {
    }

    public Turno(Long localId, Empleado empleado, DiaSemana diaSemana, LocalTime horaInicio, LocalTime horaFin) {
        this.localId = localId;
        this.empleado = empleado;
        this.diaSemana = diaSemana;
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
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

    public DiaSemana getDiaSemana() {
        return diaSemana;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public LocalTime getHoraFin() {
        return horaFin;
    }
}
