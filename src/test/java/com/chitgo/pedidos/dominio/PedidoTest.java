package com.chitgo.pedidos.dominio;

import static com.chitgo.pruebas.Entidades.con;
import static com.chitgo.pruebas.Entidades.conId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.chitgo.compartido.dominio.ReglaDeNegocioException;

class PedidoTest {

    private static final Instant T = Instant.parse("2026-10-20T20:00:00Z");

    private static Pedido pedido(EstadoPedido estado) {
        Mesa mesa = conId(new Mesa(1L, "1", "qr-1"), 3L);
        Cuenta cuenta = conId(new Cuenta(1L, mesa, 5L, T), 7L);
        ClienteSesion sesion = conId(new ClienteSesion(1L, cuenta, "token-de-A", true), 10L);
        Pedido pedido = conId(new Pedido(1L, cuenta, sesion, UUID.randomUUID().toString(), T), 5L);
        new ItemPedido(1L, pedido, 1L, (short) 2, new BigDecimal("9500.00"), "sin huevo");
        new ItemPedido(1L, pedido, 2L, (short) 1, new BigDecimal("4000.00"), null);
        return con(pedido, "estado", estado);
    }

    @Test
    void el_cliente_cancela_un_pedido_pendiente() {
        Pedido p = pedido(EstadoPedido.PENDIENTE_APROBACION);
        p.cancelarPorCliente(T);
        assertEquals(EstadoPedido.CANCELADO, p.getEstado());
        assertEquals(T, p.getCanceladoEn());
        assertEquals(null, p.getCanceladoPorUsuarioId()); // sin usuario = lo canceló el cliente
    }

    @Test
    void el_cliente_no_cancela_un_pedido_aprobado_y_el_mensaje_es_el_de_la_historia() {
        Pedido p = pedido(EstadoPedido.APROBADO);
        ReglaDeNegocioException e = assertThrows(ReglaDeNegocioException.class, () -> p.cancelarPorCliente(T));
        assertEquals("El pedido ya fue aprobado. Si necesitás cambiarlo, llamá al mozo.", e.getMessage());
        assertEquals(EstadoPedido.APROBADO, p.getEstado());
    }

    @Test
    void un_pedido_en_preparacion_no_se_cancela() {
        Pedido p = pedido(EstadoPedido.EN_PREPARACION);
        assertThrows(ReglaDeNegocioException.class, () -> p.cancelarPorCliente(T));
    }

    @Test
    void cocina_pasa_de_aprobado_a_en_preparacion_y_a_listo_guardando_las_horas() {
        Pedido p = pedido(EstadoPedido.APROBADO);
        p.iniciarPreparacion(T.plusSeconds(60));
        p.marcarListo(T.plusSeconds(600));
        assertEquals(EstadoPedido.LISTO, p.getEstado());
        assertNotNull(p.getEnPreparacionEn());
        assertEquals(T.plusSeconds(600), p.getListoEn());
    }

    @Test
    void no_se_saltea_un_estado() {
        Pedido p = pedido(EstadoPedido.APROBADO);
        assertThrows(ReglaDeNegocioException.class, () -> p.marcarListo(T));
        assertEquals(EstadoPedido.APROBADO, p.getEstado());
    }

    @Test
    void no_se_vuelve_atras() {
        Pedido p = pedido(EstadoPedido.LISTO);
        assertThrows(ReglaDeNegocioException.class, () -> p.iniciarPreparacion(T));
    }

    @Test
    void solo_se_empieza_a_preparar_un_pedido_aprobado() {
        Pedido p = pedido(EstadoPedido.PENDIENTE_APROBACION);
        assertThrows(ReglaDeNegocioException.class, () -> p.iniciarPreparacion(T));
    }

    @Test
    void el_total_suma_cantidad_por_precio_de_cada_item() {
        assertEquals(new BigDecimal("23000.00"), pedido(EstadoPedido.APROBADO).total());
    }
}
