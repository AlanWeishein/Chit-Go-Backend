package com.chitgo.pedidos.dominio;

import static com.chitgo.pruebas.Entidades.con;
import static com.chitgo.pruebas.Entidades.conId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class CuentaTest {

    private static final Instant T = Instant.parse("2026-10-20T20:00:00Z");

    private static Cuenta cuenta(EstadoCuenta estado) {
        Mesa mesa = conId(new Mesa(1L, "1", "qr-1"), 3L);
        return con(conId(new Cuenta(1L, mesa, 5L, T), 7L), "estado", estado);
    }

    @Test
    void con_la_cuenta_solicitada_un_pedido_nuevo_la_vuelve_a_abierta() {
        Cuenta c = cuenta(EstadoCuenta.SOLICITADA);
        con(c, "solicitadaEn", T);
        assertTrue(c.reabrirSiEstabaSolicitada());
        assertEquals(EstadoCuenta.ABIERTA, c.getEstado());
        assertEquals(null, c.getSolicitadaEn());
    }

    @Test
    void una_cuenta_abierta_no_cambia() {
        Cuenta c = cuenta(EstadoCuenta.ABIERTA);
        assertFalse(c.reabrirSiEstabaSolicitada());
        assertEquals(EstadoCuenta.ABIERTA, c.getEstado());
    }

    @Test
    void una_cuenta_cerrada_no_se_reabre_y_no_acepta_pedidos() {
        Cuenta c = cuenta(EstadoCuenta.CERRADA);
        assertFalse(c.reabrirSiEstabaSolicitada());
        assertFalse(c.aceptaPedidos());
    }
}
