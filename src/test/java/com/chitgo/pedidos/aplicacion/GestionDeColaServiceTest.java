package com.chitgo.pedidos.aplicacion;

import static com.chitgo.pedidos.aplicacion.DatosSprint2.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import com.chitgo.auditoria.dominio.puertos.Auditor;
import com.chitgo.compartido.dominio.*;
import com.chitgo.pedidos.dominio.*;
import com.chitgo.pedidos.dominio.puertos.*;

class GestionDeColaServiceTest {
    private final PedidoRepository pedidos = mock(PedidoRepository.class);
    private final CuentaRepository cuentas = mock(CuentaRepository.class);
    private final PublicadorDeEventos eventos = mock(PublicadorDeEventos.class);
    private final Auditor auditor = mock(Auditor.class);
    private final Cuenta cuenta = cuenta(10, mesa(1));
    private final GestionDeColaService servicio = new GestionDeColaService(pedidos, cuentas, eventos, auditor, () -> AHORA);

    @Test
    void prioridadSeGuardaAuditaYReordenaPorEventoSinCambiarLaHoraDeLlegada() {
        Pedido pedido = pedido(5, cuenta, EstadoPedido.APROBADO);
        when(pedidos.buscarPorId(5L, 1L)).thenReturn(Optional.of(pedido));
        servicio.priorizar(CARLA, 5L, true);
        assertThat(pedido.isPrioridadManual()).isTrue();
        verify(pedidos).guardar(pedido);
        verify(auditor).registrar(eq(1L), eq(40L), eq("CAMBIAR_PRIORIDAD"), eq("PEDIDO"), eq(5L),
                eq(java.util.Map.of("prioridadManual", false)), eq(java.util.Map.of("prioridadManual", true)));
        verify(eventos).aCocina(eq(1L), argThat(e -> e.tipo() == TipoEvento.PRIORIDAD_CAMBIADA && e.detalle().equals("true")));
        servicio.priorizar(CARLA, 5L, true);
        verify(pedidos, times(1)).guardar(pedido);
        servicio.priorizar(CARLA, 5L, false);
        assertThat(pedido.isPrioridadManual()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPedido.class, mode = EnumSource.Mode.EXCLUDE, names = "APROBADO")
    void prioridadSoloEnLaColaDeCocina(EstadoPedido estado) {
        when(pedidos.buscarPorId(5L, 1L)).thenReturn(Optional.of(pedido(5, cuenta, estado)));
        assertThatThrownBy(() -> servicio.priorizar(CARLA, 5L, false)).isInstanceOf(ReglaDeNegocioException.class);
        verifyNoInteractions(cuentas, auditor, eventos);
    }

    @Test
    void cancelaPedidosEnterosPendientesYAprobadosConUnAvisoPorPedido() {
        Pedido pendiente = pedido(5, cuenta, EstadoPedido.PENDIENTE_APROBACION);
        Pedido aprobado = pedido(6, cuenta, EstadoPedido.APROBADO);
        when(pedidos.buscarPorId(5L, 1L)).thenReturn(Optional.of(pendiente));
        when(pedidos.buscarPorId(6L, 1L)).thenReturn(Optional.of(aprobado));
        servicio.cancelar(CARLA, List.of(6L, 5L));
        assertThat(List.of(pendiente, aprobado)).allSatisfy(p -> {
            assertThat(p.getEstado()).isEqualTo(EstadoPedido.CANCELADO);
            assertThat(p.getCanceladoPorUsuarioId()).isEqualTo(40L);
            assertThat(p.getCanceladoEn()).isEqualTo(AHORA);
        });
        verify(cuentas, times(1)).subirVersion(cuenta);
        verify(eventos, times(2)).aCliente(eq("cliente"), argThat(e -> e.tipo() == TipoEvento.CANCELADO));
        verify(eventos, times(2)).aCocina(eq(1L), argThat(e -> e.tipo() == TipoEvento.PEDIDO_CANCELADO));
        verify(auditor, times(2)).registrar(eq(1L), eq(40L), eq("CANCELAR_PEDIDO"), eq("PEDIDO"), any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPedido.class, names = {"EN_PREPARACION", "LISTO", "SERVIDO", "CANCELADO", "RECHAZADO"})
    void unPedidoNoCancelableRechazaElLoteAntesDeTocarLosDemas(EstadoPedido estado) {
        Pedido primero = pedido(5, cuenta, EstadoPedido.APROBADO);
        when(pedidos.buscarPorId(5L, 1L)).thenReturn(Optional.of(primero));
        when(pedidos.buscarPorId(6L, 1L)).thenReturn(Optional.of(pedido(6, cuenta, estado)));
        assertThatThrownBy(() -> servicio.cancelar(CARLA, List.of(5L, 6L))).isInstanceOf(ReglaDeNegocioException.class);
        assertThat(primero.getEstado()).isEqualTo(EstadoPedido.APROBADO);
        verify(pedidos, never()).guardar(any());
        verifyNoInteractions(cuentas, auditor, eventos);
    }

    @Test
    void unIdDeOtroLocalRechazaElLoteSinCancelarLosOtros() {
        Pedido primero = pedido(5, cuenta, EstadoPedido.APROBADO);
        when(pedidos.buscarPorId(5L, 1L)).thenReturn(Optional.of(primero));
        assertThatThrownBy(() -> servicio.cancelar(CARLA, List.of(5L, 99L))).isInstanceOf(NoEncontradoException.class);
        assertThat(primero.getEstado()).isEqualTo(EstadoPedido.APROBADO);
        verifyNoInteractions(cuentas, eventos, auditor);
    }

    @Test
    void rechazaLotesVaciosDuplicadosNulosONegativos() {
        for (List<Long> ids : List.of(List.<Long>of(), List.of(1L, 1L), Arrays.asList(1L, null), List.of(-1L))) {
            assertThatThrownBy(() -> servicio.cancelar(CARLA, ids)).isInstanceOf(SolicitudInvalidaException.class);
        }
        verifyNoInteractions(pedidos, cuentas, eventos, auditor);
    }

    @Test
    void elConflictoConUnCierreNoNotificaNiCancela() {
        Pedido pedido = pedido(5, cuenta, EstadoPedido.APROBADO);
        when(pedidos.buscarPorId(5L, 1L)).thenReturn(Optional.of(pedido));
        doThrow(new ConflictoDeConcurrenciaException("cerrada")).when(cuentas).subirVersion(cuenta);
        assertThatThrownBy(() -> servicio.cancelar(CARLA, List.of(5L))).isInstanceOf(ConflictoDeConcurrenciaException.class);
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.APROBADO);
        verifyNoInteractions(eventos, auditor);
    }
}
