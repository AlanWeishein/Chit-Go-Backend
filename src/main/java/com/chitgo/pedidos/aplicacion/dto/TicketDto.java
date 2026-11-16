package com.chitgo.pedidos.aplicacion.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * El ticket no fiscal, armado para que el front lo pinte tal cual (RF-18). La letra y la leyenda viajan en el
 * JSON y no son constantes de la presentación (decidido el 29/10). {@code numero} es el id del ticket y
 * {@code mesa} es el número que se muestra, no el id.
 */
public record TicketDto(Long numero, String local, String mesa, Instant emitidoEn, List<ItemDeTicketDto> items,
                        BigDecimal total, String letra, String leyenda) {

    public static final String LETRA = "X";
    public static final String LEYENDA = "DOCUMENTO NO VÁLIDO COMO FACTURA";
}
