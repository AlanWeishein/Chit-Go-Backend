package com.chitgo.pedidos.dominio;

import static com.chitgo.pruebas.Entidades.con;
import static com.chitgo.pruebas.Entidades.conId;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class ColaDeCocinaTest {

    private static Pedido aprobado(long id, boolean prioritario, String hora) {
        Instant llegada = Instant.parse("2026-10-20T" + hora + ":00Z");
        Mesa mesa = conId(new Mesa(1L, "1", "qr-1"), 3L);
        Cuenta cuenta = conId(new Cuenta(1L, mesa, 5L, llegada), 7L);
        ClienteSesion sesion = conId(new ClienteSesion(1L, cuenta, "token-" + id, true), 10L);
        Pedido p = conId(new Pedido(1L, cuenta, sesion, "uuid-" + id, llegada.minusSeconds(30)), id);
        con(p, "estado", EstadoPedido.APROBADO);
        con(p, "resueltoEn", llegada);
        return con(p, "prioridadManual", prioritario);
    }

    private static List<Long> ids(ColaDeCocina cola) {
        return cola.enOrden().stream().map(Pedido::getId).toList();
    }

    @Test
    void sin_prioridad_manda_el_orden_de_llegada() {
        ColaDeCocina cola = ColaDeCocina.con(List.of(
                aprobado(3, false, "20:05"), aprobado(1, false, "20:00"), aprobado(2, false, "20:02")));
        assertEquals(List.of(1L, 2L, 3L), ids(cola));
    }

    @Test
    void CP_24_02_el_prioritario_va_primero_y_despues_el_orden_de_llegada() {
        ColaDeCocina cola = ColaDeCocina.con(List.of(
                aprobado(1, false, "20:00"), aprobado(2, true, "20:02"), aprobado(3, false, "20:05")));
        assertEquals(List.of(2L, 1L, 3L), ids(cola));
    }

    @Test
    void CP_21_02_entre_prioritarios_manda_el_orden_de_llegada() {
        ColaDeCocina cola = ColaDeCocina.con(List.of(
                aprobado(1, false, "20:00"), aprobado(2, true, "20:02"), aprobado(3, true, "20:05")));
        assertEquals(List.of(2L, 3L, 1L), ids(cola));
    }

    @Test
    void siguiente_es_el_primero_de_la_cola_sin_sacarlo() {
        ColaDeCocina cola = ColaDeCocina.con(List.of(aprobado(1, false, "20:00"), aprobado(2, true, "20:02")));
        assertEquals(2L, cola.siguiente().orElseThrow().getId());
        assertEquals(2, cola.enOrden().size());
    }

    @Test
    void una_cola_vacia_no_tiene_siguiente() {
        assertEquals(true, ColaDeCocina.con(List.of()).siguiente().isEmpty());
    }
}
