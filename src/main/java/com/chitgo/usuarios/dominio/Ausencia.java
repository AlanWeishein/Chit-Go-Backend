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

import com.chitgo.compartido.dominio.SolicitudInvalidaException;

/**
 * Vacaciones, franco o baja temporal, aplicada encima del Turno. {@code desde} y {@code hasta} son días del local
 * y los dos cuentan: el día siguiente a {@code hasta} vuelve. Sin {@code hasta}, no tiene fecha de vuelta. No se
 * borra: terminarla es ponerle {@code hasta}, así queda de cuándo a cuándo faltó.
 */
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

    /** @throws SolicitudInvalidaException si falta el tipo o el inicio, o si termina antes de empezar */
    public Ausencia(Long localId, Empleado empleado, TipoAusencia tipo, LocalDate desde, LocalDate hasta, Long creadaPorUsuarioId) {
        if (tipo == null) {
            throw new SolicitudInvalidaException("Falta el tipo de ausencia");
        }
        if (desde == null) {
            throw new SolicitudInvalidaException("Falta la fecha de inicio");
        }
        verificarFin(desde, hasta);
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

    /**
     * Termina la ausencia o cambia su fecha de vuelta.
     *
     * @return false si ya terminaba ese día
     * @throws SolicitudInvalidaException si falta la fecha o es anterior a {@code desde}
     */
    public boolean cambiarHasta(LocalDate hasta) {
        if (hasta == null) {
            throw new SolicitudInvalidaException("Falta la fecha de fin");
        }
        verificarFin(desde, hasta);
        if (hasta.equals(this.hasta)) {
            return false;
        }
        this.hasta = hasta;
        return true;
    }

    private static void verificarFin(LocalDate desde, LocalDate hasta) {
        if (hasta != null && hasta.isBefore(desde)) {
            throw new SolicitudInvalidaException("La ausencia no puede terminar antes de empezar");
        }
    }
}
