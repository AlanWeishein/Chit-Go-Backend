package com.chitgo.pedidos.dominio;

import java.math.BigDecimal;
import java.time.Instant;

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
import jakarta.persistence.Version;

/** Cuenta de una mesa. Una mesa tiene a lo sumo una no cerrada. */
@Entity
@Table(name = "cuenta")
public class Cuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long localId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Mesa mesa;

    private Long mozoId;

    @Enumerated(EnumType.STRING)
    private EstadoCuenta estado = EstadoCuenta.ABIERTA;

    private Instant abiertaEn;

    private Instant solicitadaEn;

    private Instant cerradaEn;

    private Long cerradaPorUsuarioId;

    @Column(precision = 10, scale = 2)
    private BigDecimal total;

    @Version
    private Integer version;

    protected Cuenta() {
    }

    public Cuenta(Long localId, Mesa mesa, Long mozoId, Instant abiertaEn) {
        this.localId = localId;
        this.mesa = mesa;
        this.mozoId = mozoId;
        this.abiertaEn = abiertaEn;
    }

    public Long getId() {
        return id;
    }

    public Long getLocalId() {
        return localId;
    }

    public Mesa getMesa() {
        return mesa;
    }

    public Long getMozoId() {
        return mozoId;
    }

    public EstadoCuenta getEstado() {
        return estado;
    }

    public Instant getAbiertaEn() {
        return abiertaEn;
    }

    public Instant getSolicitadaEn() {
        return solicitadaEn;
    }

    public Instant getCerradaEn() {
        return cerradaEn;
    }

    public Long getCerradaPorUsuarioId() {
        return cerradaPorUsuarioId;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public Integer getVersion() {
        return version;
    }
}
