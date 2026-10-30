package com.chitgo.pedidos.dominio;

/** Estado de un Pedido. */
public enum EstadoPedido {
    PENDIENTE_APROBACION,
    APROBADO,
    RECHAZADO,
    CANCELADO,
    EN_PREPARACION,
    LISTO,
    SERVIDO
}
