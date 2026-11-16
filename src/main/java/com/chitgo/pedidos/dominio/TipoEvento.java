package com.chitgo.pedidos.dominio;

/** Eventos de tiempo real del contrato (sección 11 de chit-go-endpoints-api). */
public enum TipoEvento {
    // Caja
    PEDIDO_ENVIADO, PEDIDO_CANCELADO, CUENTA_REABIERTA, ALERTA_DEMORA,
    CUENTA_CERRADA, // Caja cerró la cuenta: las otras cajas la sacan de cuentas por cobrar y los mozos ven la mesa libre
    PEDIDO_RESUELTO, // otra caja (o esta) lo aprobó o rechazó: todas lo sacan de la cola (21/10)
    // Cocina
    PEDIDO_APROBADO, PRIORIDAD_CAMBIADA,
    // Mozo y alertas
    AVISO_CREADO, AVISO_ATENDIDO, CUENTA_SOLICITADA, PEDIDO_LISTO, MESA_SIN_MOZO,
    // Cliente
    APROBADO, RECHAZADO, EN_PREPARACION, LISTO, SERVIDO, CANCELADO, PLATO_BAJA,
    PLATO_DISPONIBLE // un plato apagado volvió a la carta (HU-09, 29/10)
}
