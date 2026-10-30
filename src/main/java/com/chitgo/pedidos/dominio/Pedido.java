package com.chitgo.pedidos.dominio;

import java.math.BigDecimal;
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
import com.chitgo.compartido.dominio.ReglaDeNegocioException;
import com.chitgo.compartido.dominio.SolicitudInvalidaException;
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

    // ----- máquina de estados: las reglas viven acá -----
    // Servir lo suma quien haga HU-05, en esta misma clase.

    /** Caja aprueba (HU-07): solo un pedido pendiente. Queda quién lo resolvió y a qué hora. */
    public void aprobar(Long usuarioId, Instant ahora) {
        exigirPendiente();
        estado = EstadoPedido.APROBADO;
        resueltoPorUsuarioId = usuarioId;
        resueltoEn = ahora;
    }

    /** Caja rechaza (HU-07): solo un pedido pendiente y siempre con un motivo de la lista. */
    public void rechazar(MotivoRechazo motivo, Long usuarioId, Instant ahora) {
        if (motivo == null) {
            throw new SolicitudInvalidaException("Elegí el motivo del rechazo");
        }
        exigirPendiente();
        estado = EstadoPedido.RECHAZADO;
        motivoRechazo = motivo;
        resueltoPorUsuarioId = usuarioId;
        resueltoEn = ahora;
    }

    private void exigirPendiente() {
        if (estado != EstadoPedido.PENDIENTE_APROBACION) {
            throw new ReglaDeNegocioException(mensajeNoResoluble());
        }
    }

    /** El cliente cancela: solo mientras Caja no lo resolvió. Cancelado sin usuario = lo canceló el cliente. */
    public void cancelarPorCliente(Instant ahora) {
        cancelar(OrigenDeCancelacion.CLIENTE, null, ahora);
    }

    /**
     * Cancela según quién lo pide: cada origen dice desde qué estados puede. Queda quién y a qué hora;
     * el usuario va en null solo con el cliente. El origen no se guarda (queda en la auditoría).
     */
    public void cancelar(OrigenDeCancelacion origen, Long usuarioId, Instant ahora) {
        if (origen == null) {
            throw new IllegalArgumentException("Falta el origen de la cancelación");
        }
        // Errores de programación, no del usuario: cancelado_por_usuario_id en NULL tiene que querer decir cliente.
        if (origen == OrigenDeCancelacion.CLIENTE && usuarioId != null) {
            throw new IllegalArgumentException("El cliente cancela sin usuario");
        }
        if (origen != OrigenDeCancelacion.CLIENTE && usuarioId == null) {
            throw new IllegalArgumentException("Falta el usuario que cancela");
        }
        if (!origen.permite(estado)) {
            throw new ReglaDeNegocioException(origen == OrigenDeCancelacion.CLIENTE
                    ? mensajeNoCancelable() : mensajeNoCancelablePorCaja(origen));
        }
        estado = EstadoPedido.CANCELADO;
        canceladoEn = ahora;
        canceladoPorUsuarioId = usuarioId;
    }

    /** Cocina empieza: solo desde Aprobado. */
    public void iniciarPreparacion(Instant ahora) {
        if (estado != EstadoPedido.APROBADO) {
            throw new ReglaDeNegocioException("Solo se puede empezar a preparar un pedido aprobado");
        }
        estado = EstadoPedido.EN_PREPARACION;
        enPreparacionEn = ahora;
    }

    /** Cocina termina: solo desde En preparación. No se saltea ni se vuelve atrás. */
    public void marcarListo(Instant ahora) {
        if (estado != EstadoPedido.EN_PREPARACION) {
            throw new ReglaDeNegocioException("Solo se puede marcar listo un pedido en preparación");
        }
        estado = EstadoPedido.LISTO;
        listoEn = ahora;
    }

    /** Qué le dice al cliente si ya no puede cancelar, según el estado en que está el pedido. */
    public String mensajeNoCancelable() {
        return switch (estado) {
            case APROBADO -> "El pedido ya fue aprobado. Si necesitás cambiarlo, llamá al mozo.";
            case EN_PREPARACION -> "El pedido ya se está preparando y no se puede cancelar";
            case CANCELADO -> "El pedido ya fue cancelado";
            case RECHAZADO -> "El pedido ya fue rechazado";
            default -> "El pedido ya no se puede cancelar";
        };
    }

    /** Qué le dice a Caja (a mano o al cerrar la cuenta) si ya no puede cancelarlo. */
    private String mensajeNoCancelablePorCaja(OrigenDeCancelacion origen) {
        if (estado == EstadoPedido.CANCELADO) {
            return "El pedido ya fue cancelado";
        }
        if (origen == OrigenDeCancelacion.CAJA && estado == EstadoPedido.EN_PREPARACION) {
            return "El pedido ya se está preparando y no se puede cancelar";
        }
        return "El pedido ya no se puede cancelar";
    }

    /** Qué le dice a Caja si ya no puede aprobarlo ni rechazarlo: lo canceló el cliente u otra caja lo resolvió. */
    public String mensajeNoResoluble() {
        return estado == EstadoPedido.CANCELADO ? "El pedido ya fue cancelado" : "El pedido ya fue resuelto";
    }

    /**
     * Sin servir es lo que no está Servido, Rechazado ni Cancelado: lo que el cierre de la cuenta cancela (20/10).
     * Coincide con los estados desde los que cancela CIERRE_DE_CUENTA, y por eso se apoya en ellos.
     */
    public boolean estaSinServir() {
        return OrigenDeCancelacion.CIERRE_DE_CUENTA.permite(estado);
    }

    /** Suma al total de la cuenta todo lo que no quedó Rechazado ni Cancelado (CP-22-02). */
    public boolean cuentaEnElTotal() {
        return estado != EstadoPedido.RECHAZADO && estado != EstadoPedido.CANCELADO;
    }

    /** Suma de cantidad por precio unitario de cada ítem (el precio del momento del pedido). */
    public BigDecimal total() {
        return items.stream()
                .map(i -> i.getPrecioUnitario().multiply(BigDecimal.valueOf(i.getCantidad())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
