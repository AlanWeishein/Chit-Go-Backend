package com.chitgo.pedidos.dominio;

import java.time.Instant;

/**
 * Payload de los avisos por WebSocket. Sin precios ni datos del personal (RF-31). La hora viaja como texto
 * ISO-8601 para no depender de cómo serialice fechas el conversor de mensajes.
 */
public record EventoTiempoReal(TipoEvento tipo, Long pedidoId, Long mesaId, String estado, String detalle,
                               String ocurridoEn) {

    public static EventoTiempoReal de(TipoEvento tipo, Long pedidoId, Long mesaId, String estado,
                                      String detalle, Instant ocurridoEn) {
        return new EventoTiempoReal(tipo, pedidoId, mesaId, estado, detalle, ocurridoEn.toString());
    }
}
