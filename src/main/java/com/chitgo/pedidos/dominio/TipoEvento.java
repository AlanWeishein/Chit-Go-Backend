package com.chitgo.pedidos.dominio;

/** Eventos de tiempo real del contrato (sección 11 de chit-go-endpoints-api). */
public enum TipoEvento {
    // Caja
    PEDIDO_ENVIADO, PEDIDO_CANCELADO, CUENTA_REABIERTA, ALERTA_DEMORA,
    // Cocina
    PEDIDO_APROBADO, PRIORIDAD_CAMBIADA,
    // Mozo y alertas
    AVISO_CREADO, PEDIDO_LISTO, MESA_SIN_MOZO,
    // Cliente
    APROBADO, RECHAZADO, EN_PREPARACION, LISTO, SERVIDO, CANCELADO, PLATO_BAJA
}
