package com.chitgo.pedidos.dominio;

import static com.chitgo.pruebas.Entidades.con;
import static com.chitgo.pruebas.Entidades.conId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.chitgo.compartido.dominio.ReglaDeNegocioException;

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

    @ParameterizedTest
    @EnumSource(value = EstadoCuenta.class, names = {"ABIERTA", "SOLICITADA"})
    void cerrar_desde_abierta_o_solicitada_guarda_quien_cuando_y_el_total(EstadoCuenta desde) {
        Cuenta c = cuenta(desde);
        Instant ahora = T.plusSeconds(3600);

        c.cerrar(6L, new BigDecimal("23000.00"), ahora);

        assertEquals(EstadoCuenta.CERRADA, c.getEstado());
        assertEquals(ahora, c.getCerradaEn());
        assertEquals(6L, c.getCerradaPorUsuarioId());
        assertEquals(new BigDecimal("23000.00"), c.getTotal());
        assertFalse(c.aceptaPedidos());
    }

    @Test
    void cerrar_una_cuenta_ya_cerrada_es_409_y_no_pisa_el_primer_cierre() {
        Cuenta c = cuenta(EstadoCuenta.ABIERTA);
        Instant primero = T.plusSeconds(3600);
        c.cerrar(6L, new BigDecimal("23000.00"), primero);

        ReglaDeNegocioException e = assertThrows(ReglaDeNegocioException.class,
                () -> c.cerrar(9L, new BigDecimal("1.00"), primero.plusSeconds(60)));

        assertEquals("La cuenta ya está cerrada", e.getMessage());
        assertEquals(primero, c.getCerradaEn());
        assertEquals(6L, c.getCerradaPorUsuarioId());
        assertEquals(new BigDecimal("23000.00"), c.getTotal());
    }

    @Test
    void cerrar_no_toca_solicitadaEn() {
        Cuenta c = con(cuenta(EstadoCuenta.SOLICITADA), "solicitadaEn", T.plusSeconds(60));

        c.cerrar(6L, new BigDecimal("23000.00"), T.plusSeconds(3600));

        assertEquals(T.plusSeconds(60), c.getSolicitadaEn());
    }

    @Test
    void cerrar_sin_usuario_sin_total_o_sin_hora_es_un_error_de_programacion_y_no_cambia_la_cuenta() {
        Cuenta c = cuenta(EstadoCuenta.SOLICITADA);

        assertThrows(IllegalArgumentException.class, () -> c.cerrar(null, new BigDecimal("1.00"), T));
        assertThrows(IllegalArgumentException.class, () -> c.cerrar(6L, null, T));
        assertThrows(IllegalArgumentException.class, () -> c.cerrar(6L, new BigDecimal("1.00"), null));

        assertEquals(EstadoCuenta.SOLICITADA, c.getEstado());
        assertEquals(null, c.getCerradaEn());
        assertEquals(null, c.getTotal());
    }
}
