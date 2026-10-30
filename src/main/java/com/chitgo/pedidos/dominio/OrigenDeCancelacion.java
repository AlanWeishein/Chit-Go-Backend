package com.chitgo.pedidos.dominio;

import java.util.EnumSet;
import java.util.Set;

/** Quién cancela un Pedido y desde qué estados puede hacerlo (ST-09, decidido el 29/10). */
public enum OrigenDeCancelacion {

    /** El cliente iniciador, mientras Caja no lo resolvió (HU-02). */
    CLIENTE(EnumSet.of(EstadoPedido.PENDIENTE_APROBACION)),

    /** Caja, a mano o por la baja de un plato (RF-21, HU-09). */
    CAJA(EnumSet.of(EstadoPedido.PENDIENTE_APROBACION, EstadoPedido.APROBADO)),

    /** Caja cierra la cuenta y cancela lo que no se sirvió (HU-10). */
    CIERRE_DE_CUENTA(EnumSet.of(EstadoPedido.PENDIENTE_APROBACION, EstadoPedido.APROBADO,
            EstadoPedido.EN_PREPARACION, EstadoPedido.LISTO));

    private final Set<EstadoPedido> estadosCancelables;

    OrigenDeCancelacion(Set<EstadoPedido> estadosCancelables) {
        this.estadosCancelables = estadosCancelables;
    }

    public boolean permite(EstadoPedido estado) {
        return estadosCancelables.contains(estado);
    }
}
