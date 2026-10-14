package com.chitgo.pedidos.dominio;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Sesión anónima de un cliente que escaneó el QR de la mesa. */
@Entity
@Table(name = "cliente_sesion")
public class ClienteSesion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long localId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Cuenta cuenta;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private String tokenSesion;

    private boolean esIniciador;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant creadoEn;

    protected ClienteSesion() {
    }

    public ClienteSesion(Long localId, Cuenta cuenta, String tokenSesion, boolean esIniciador) {
        this.localId = localId;
        this.cuenta = cuenta;
        this.tokenSesion = tokenSesion;
        this.esIniciador = esIniciador;
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

    public String getTokenSesion() {
        return tokenSesion;
    }

    public boolean isEsIniciador() {
        return esIniciador;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
