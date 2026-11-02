package com.chitgo.pedidos.dominio;

import java.time.Instant;

/**
 * Payload de los avisos por WebSocket. Sin precios ni datos del personal (RF-31). La hora viaja como texto
 * ISO-8601 para no depender de cómo serialice fechas el conversor de mensajes. {@code cuentaId} lo traen
 * los eventos de la cuenta y los avisos; en los de un pedido va en null. {@code platoId} lo traen solo
 * PLATO_BAJA y PLATO_DISPONIBLE (HU-09).
 */
public record EventoTiempoReal(TipoEvento tipo, Long pedidoId, Long mesaId, Long cuentaId, Long platoId,
                               String estado, String detalle, String ocurridoEn) {

    public static EventoTiempoReal de(TipoEvento tipo, Long pedidoId, Long mesaId, String estado,
                                      String detalle, Instant ocurridoEn) {
        return new EventoTiempoReal(tipo, pedidoId, mesaId, null, null, estado, detalle, ocurridoEn.toString());
    }

    /** Un evento de la cuenta: sin pedido. */
    public static EventoTiempoReal deCuenta(TipoEvento tipo, Long cuentaId, Long mesaId, String estado,
                                            Instant ocurridoEn) {
        return new EventoTiempoReal(tipo, null, mesaId, cuentaId, null, estado, null, ocurridoEn.toString());
    }

    /** Un plato cambió de disponibilidad: el celular vuelve a pedir el menú (decidido el 29/10). */
    public static EventoTiempoReal dePlato(TipoEvento tipo, Long platoId, Instant ocurridoEn) {
        return new EventoTiempoReal(tipo, null, null, null, platoId, null, null, ocurridoEn.toString());
    }
}
