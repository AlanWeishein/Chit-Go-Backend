package com.chitgo.pedidos.dominio;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** Comprobante interno que se emite al cerrar la Cuenta. No es una factura. */
@Entity
@Table(name = "ticket")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long localId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    private Cuenta cuenta;

    private Instant emitidoEn;

    @Column(precision = 10, scale = 2)
    private BigDecimal total;

    protected Ticket() {
    }

    public Ticket(Long localId, Cuenta cuenta, Instant emitidoEn, BigDecimal total) {
        this.localId = localId;
        this.cuenta = cuenta;
        this.emitidoEn = emitidoEn;
        this.total = total;
    }

    public Long getId() {
        return id;
    }

    public Long getLocalId() {
        return localId;
    }

    public Cuenta getCuenta() {
        return cuenta;
    }

    public Instant getEmitidoEn() {
        return emitidoEn;
    }

    public BigDecimal getTotal() {
        return total;
    }
}
