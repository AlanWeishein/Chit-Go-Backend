package com.chitgo.pedidos.dominio;

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

/** Mesa del local. No guarda estado: se deriva de su Cuenta. */
@Entity
@Table(name = "mesa")
public class Mesa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long localId;

    private String numero;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private String tokenQr;

    private boolean inhabilitada = false;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant creadoEn;

    protected Mesa() {
    }

    public Mesa(Long localId, String numero, String tokenQr) {
        this.localId = localId;
        this.numero = numero;
        this.tokenQr = tokenQr;
    }

    public Long getId() {
        return id;
    }

    public Long getLocalId() {
        return localId;
    }

    public String getNumero() {
        return numero;
    }

    public String getTokenQr() {
        return tokenQr;
    }

    public boolean isInhabilitada() {
        return inhabilitada;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
