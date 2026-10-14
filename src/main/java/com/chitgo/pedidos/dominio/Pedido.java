package com.chitgo.pedidos.dominio;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Pedido de una sesión de cliente. El estado vive acá y no en ItemPedido. */
@Entity
@Table(name = "pedido")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long localId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Cuenta cuenta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ClienteSesion clienteSesion;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private String uuidCliente;

    @Enumerated(EnumType.STRING)
    private EstadoPedido estado = EstadoPedido.PENDIENTE_APROBACION;

    @Enumerated(EnumType.STRING)
    private MotivoRechazo motivoRechazo;

    private boolean prioridadManual = false;

    private Long resueltoPorUsuarioId;

    private Long servidoPorUsuarioId;

    private Long canceladoPorUsuarioId;

    private Instant enviadoEn;

    private Instant resueltoEn;

    private Instant enPreparacionEn;

    private Instant listoEn;

    private Instant servidoEn;

    private Instant canceladoEn;

    @Version
    private Integer version;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemPedido> items = new ArrayList<>();

    protected Pedido() {
    }

    public Pedido(Long localId, Cuenta cuenta, ClienteSesion clienteSesion, String uuidCliente, Instant enviadoEn) {
        this.localId = localId;
        this.cuenta = cuenta;
        this.clienteSesion = clienteSesion;
        this.uuidCliente = uuidCliente;
        this.enviadoEn = enviadoEn;
    }

    /** Solo arma la relación: lo llama el constructor de ItemPedido. */
    void agregarItem(ItemPedido item) {
        items.add(item);
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

    public ClienteSesion getClienteSesion() {
        return clienteSesion;
    }

    public String getUuidCliente() {
        return uuidCliente;
    }

    public EstadoPedido getEstado() {
        return estado;
    }

    public MotivoRechazo getMotivoRechazo() {
        return motivoRechazo;
    }

    public boolean isPrioridadManual() {
        return prioridadManual;
    }

    public Long getResueltoPorUsuarioId() {
        return resueltoPorUsuarioId;
    }

    public Long getServidoPorUsuarioId() {
        return servidoPorUsuarioId;
    }

    public Long getCanceladoPorUsuarioId() {
        return canceladoPorUsuarioId;
    }

    public Instant getEnviadoEn() {
        return enviadoEn;
    }

    public Instant getResueltoEn() {
        return resueltoEn;
    }

    public Instant getEnPreparacionEn() {
        return enPreparacionEn;
    }

    public Instant getListoEn() {
        return listoEn;
    }

    public Instant getServidoEn() {
        return servidoEn;
    }

    public Instant getCanceladoEn() {
        return canceladoEn;
    }

    public Integer getVersion() {
        return version;
    }

    public List<ItemPedido> getItems() {
        return Collections.unmodifiableList(items);
    }
}
