package com.chitgo.pedidos.infraestructura.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

import com.chitgo.pedidos.aplicacion.ConfirmacionDeCierreRequeridaException;
import com.chitgo.pedidos.aplicacion.dto.PedidoSinServirDto;
import com.chitgo.pedidos.aplicacion.dto.PlatoSinServirDto;
import com.chitgo.pedidos.dominio.MotivoDeConfirmacion;

/** El 409 que pide confirmar el cierre: sin Spring, el handler se llama directo. */
class ErroresDeCierreTest {

    private static final String MENSAJE_NADIE = "Esta cuenta no la pidió nadie. ¿La cerramos igual?";
    private static final String MENSAJE_SIN_SERVIR = "Hay pedidos sin servir. ¿Estás seguro de que querés cerrar la cuenta?";

    private final ErroresDeCierre errores = new ErroresDeCierre();

    private static PedidoSinServirDto enPreparacion41() {
        return new PedidoSinServirDto(41L, "EN_PREPARACION", List.of(new PlatoSinServirDto("Milanesa napolitana", 1)));
    }

    @Test
    void con_pedidos_sin_servir_es_409_con_el_codigo_el_motivo_y_los_pedidos() {
        ProblemDetail problema = errores.confirmarCierre(new ConfirmacionDeCierreRequeridaException(
                List.of(MotivoDeConfirmacion.PEDIDOS_SIN_SERVIR), List.of(enPreparacion41())));

        assertEquals(409, problema.getStatus());
        assertEquals(MENSAJE_SIN_SERVIR, problema.getDetail());
        assertEquals("CONFIRMAR_CIERRE", problema.getProperties().get("codigo"));
        assertEquals(List.of("PEDIDOS_SIN_SERVIR"), problema.getProperties().get("motivos"));
        assertEquals(List.of(enPreparacion41()), problema.getProperties().get("pedidosSinServir"));
    }

    @Test
    void con_los_dos_motivos_el_detail_lleva_los_dos_mensajes_separados_por_un_espacio_y_en_orden() {
        ProblemDetail problema = errores.confirmarCierre(new ConfirmacionDeCierreRequeridaException(
                List.of(MotivoDeConfirmacion.NADIE_PIDIO_LA_CUENTA, MotivoDeConfirmacion.PEDIDOS_SIN_SERVIR),
                List.of(enPreparacion41())));

        assertEquals(409, problema.getStatus());
        assertEquals(MENSAJE_NADIE + " " + MENSAJE_SIN_SERVIR, problema.getDetail());
        assertEquals(List.of("NADIE_PIDIO_LA_CUENTA", "PEDIDOS_SIN_SERVIR"), problema.getProperties().get("motivos"));
    }

    @Test
    void si_solo_nadie_pidio_la_cuenta_pedidos_sin_servir_va_vacio_y_el_detail_es_su_mensaje() {
        ProblemDetail problema = errores.confirmarCierre(new ConfirmacionDeCierreRequeridaException(
                List.of(MotivoDeConfirmacion.NADIE_PIDIO_LA_CUENTA), List.of()));

        assertEquals(MENSAJE_NADIE, problema.getDetail());
        assertEquals(List.of("NADIE_PIDIO_LA_CUENTA"), problema.getProperties().get("motivos"));
        assertEquals(List.of(), problema.getProperties().get("pedidosSinServir"));
        assertEquals("CONFIRMAR_CIERRE", problema.getProperties().get("codigo"));
    }
}
