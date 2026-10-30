package com.chitgo.pedidos.dominio;

import java.time.Instant;

/**
 * Payload de los avisos por WebSocket. Sin precios ni datos del personal (RF-31). La hora viaja como texto
 * ISO-8601 para no depender de cómo serialice fechas el conversor de mensajes. {@code cuentaId} solo lo traen
 * los eventos de la cuenta (CUENTA_CERRADA); en los de un pedido va en null.
 */
public record EventoTiempoReal(TipoEvento tipo, Long pedidoId, Long mesaId, Long cuentaId, String estado,
                               String detalle, String ocurridoEn) {

    public static EventoTiempoReal de(TipoEvento tipo, Long pedidoId, Long mesaId, String estado,
                                      String detalle, Instant ocurridoEn) {
        return new EventoTiempoReal(tipo, pedidoId, mesaId, null, estado, detalle, ocurridoEn.toString());
    }

    /** Un evento de la cuenta: sin pedido. */
    public static EventoTiempoReal deCuenta(TipoEvento tipo, Long cuentaId, Long mesaId, String estado,
                                            Instant ocurridoEn) {
        return new EventoTiempoReal(tipo, null, mesaId, cuentaId, estado, null, ocurridoEn.toString());
    }
}
