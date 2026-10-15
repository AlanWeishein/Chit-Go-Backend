package com.chitgo.pedidos.dominio;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Línea de un Pedido. El precio unitario es una copia del precio del plato al pedir. */
@Entity
@Table(name = "item_pedido")
public class ItemPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Pedido pedido;

    private Long platoId;

    private short cantidad;

    @Column(precision = 10, scale = 2)
    private BigDecimal precioUnitario;

    private String observacion;

    protected ItemPedido() {
    }

    public ItemPedido(Pedido pedido, Long platoId, short cantidad, BigDecimal precioUnitario, String observacion) {
        this.pedido = pedido;
        this.platoId = platoId;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
        this.observacion = observacion;
        pedido.agregarItem(this);
    }

    public Long getId() {
        return id;
    }

    public Pedido getPedido() {
        return pedido;
    }

    public Long getPlatoId() {
        return platoId;
    }

    public short getCantidad() {
        return cantidad;
    }

    public BigDecimal getPrecioUnitario() {
        return precioUnitario;
    }

    public String getObservacion() {
        return observacion;
    }
}
