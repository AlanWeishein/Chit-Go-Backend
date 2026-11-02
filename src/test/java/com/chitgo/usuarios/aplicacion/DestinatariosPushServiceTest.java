package com.chitgo.usuarios.aplicacion;

import static com.chitgo.pruebas.Entidades.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.chitgo.pedidos.dominio.*;
import com.chitgo.pedidos.dominio.puertos.*;
import com.chitgo.usuarios.dominio.SuscripcionPush;
import com.chitgo.usuarios.dominio.puertos.*;

class DestinatariosPushServiceTest {
    private static final Instant AHORA = Instant.parse("2026-11-01T20:00:00Z");
    private final CuentaRepository cuentas = mock(CuentaRepository.class);
    private final PedidoRepository pedidos = mock(PedidoRepository.class);
    private final AsignacionesDeMozo asignaciones = mock(AsignacionesDeMozo.class);
    private final SuscripcionesPush suscripciones = mock(SuscripcionesPush.class);
    private final DestinatariosPushService servicio = new DestinatariosPushService(cuentas, pedidos, asignaciones, suscripciones);
    private final Mesa mesa = conId(new Mesa(1L, "1", "qr"), 1L);
    private final Cuenta cuenta = conId(new Cuenta(1L, mesa, 3L, AHORA), 10L);
    private final EventoTiempoReal evento = new EventoTiempoReal(TipoEvento.AVISO_CREADO, null, 1L, 10L,
            "ABIERTA", "LLAMAR_MOZO", AHORA.toString());

    @Test
    void convierteEmpleadoAUsuarioYBuscaSoloSusDispositivosDelLocal() {
        when(cuentas.buscarPorId(10L, 1L)).thenReturn(Optional.of(cuenta));
        when(asignaciones.usuarioDelEmpleado(3L, 1L)).thenReturn(Optional.of(30L));
        when(asignaciones.mesasDe(30L, 1L)).thenReturn(Set.of(1L));
        SuscripcionPush celular = new SuscripcionPush(1L, 30L, "endpoint", "hash", "clave", "auth", AHORA);
        when(suscripciones.delUsuario(30L, 1L)).thenReturn(List.of(celular));
        assertThat(servicio.para(1L, evento)).containsExactly(celular);
        verify(suscripciones).delUsuario(30L, 1L);
        verify(suscripciones, never()).delUsuario(3L, 1L);
    }

    @Test
    void noNotificaSiElEmpleadoFueDadoDeBajaONoEstaMasAsignado() {
        when(cuentas.buscarPorId(10L, 1L)).thenReturn(Optional.of(cuenta));
        assertThat(servicio.para(1L, evento)).isEmpty();
        when(asignaciones.usuarioDelEmpleado(3L, 1L)).thenReturn(Optional.of(30L));
        when(asignaciones.mesasDe(30L, 1L)).thenReturn(Set.of(2L));
        assertThat(servicio.para(1L, evento)).isEmpty();
        verifyNoInteractions(suscripciones);
    }

    @Test
    void noNotificaUnAvisoSiLaCuentaSeCerroAntesDelEnvio() {
        cuenta.cerrar(40L, java.math.BigDecimal.ZERO, AHORA);
        when(cuentas.buscarPorId(10L, 1L)).thenReturn(Optional.of(cuenta));
        assertThat(servicio.para(1L, evento)).isEmpty();
        verifyNoInteractions(asignaciones, suscripciones);
    }

    @Test
    void unPedidoServidoAntesDeDespacharElPushNoGeneraAvisoDeListo() {
        ClienteSesion cliente = conId(new ClienteSesion(1L, cuenta, "token", true), 100L);
        Pedido pedido = conId(new Pedido(1L, cuenta, cliente, "uuid", AHORA), 5L);
        con(pedido, "estado", EstadoPedido.SERVIDO);
        when(pedidos.buscarPorId(5L, 1L)).thenReturn(Optional.of(pedido));
        assertThat(servicio.para(1L, EventoTiempoReal.de(TipoEvento.PEDIDO_LISTO, 5L, 1L, "LISTO", null, AHORA))).isEmpty();
        verifyNoInteractions(asignaciones, suscripciones);
    }
}
