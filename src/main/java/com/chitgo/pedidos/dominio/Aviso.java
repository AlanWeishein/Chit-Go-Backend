package com.chitgo.pedidos.dominio;

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
import com.chitgo.compartido.dominio.ReglaDeNegocioException;

/** Llamado de una mesa: llamar al mozo, cuenta solicitada o mesa sin mozo. */
@Entity
@Table(name = "aviso")
public class Aviso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long localId;

    private Long cuentaId;

    @Version
    private Integer version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Mesa mesa;

    @Enumerated(EnumType.STRING)
    private TipoAviso tipo;

    @Column(updatable = false)
    private Instant creadoEn = Instant.now();

    private Long atendidoPorUsuarioId;

    private Instant atendidoEn;

    protected Aviso() {
    }

    public Aviso(Long localId, Mesa mesa, TipoAviso tipo) {
        this.localId = localId;
        this.mesa = mesa;
        this.tipo = tipo;
    }

    public Aviso(Cuenta cuenta, TipoAviso tipo, Instant ahora) {
        this(cuenta.getLocalId(), cuenta.getMesa(), tipo);
        this.cuentaId = cuenta.getId();
        this.creadoEn = ahora;
    }

    public Long getCuentaId() {
        return cuentaId;
    }

    public void atender(Long usuarioId, Instant ahora) {
        if (atendidoEn != null) {
            throw new ReglaDeNegocioException("El aviso ya fue atendido");
        }
        if (usuarioId == null || ahora == null) {
            throw new IllegalArgumentException("Faltan el mozo o la hora de atención");
        }
        atendidoPorUsuarioId = usuarioId;
        atendidoEn = ahora;
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

    public TipoAviso getTipo() {
        return tipo;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Long getAtendidoPorUsuarioId() {
        return atendidoPorUsuarioId;
    }

    public Instant getAtendidoEn() {
        return atendidoEn;
    }
}
