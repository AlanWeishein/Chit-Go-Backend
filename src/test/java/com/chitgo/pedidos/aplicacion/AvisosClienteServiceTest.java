package com.chitgo.pedidos.aplicacion;

import static com.chitgo.pedidos.aplicacion.DatosSprint2.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import com.chitgo.compartido.dominio.*;
import com.chitgo.pedidos.dominio.*;
import com.chitgo.pedidos.dominio.puertos.*;

class AvisosClienteServiceTest {
    private final ClienteSesionRepository sesiones = mock(ClienteSesionRepository.class);
    private final CuentaRepository cuentas = mock(CuentaRepository.class);
    private final PedidoRepository pedidos = mock(PedidoRepository.class);
    private final AvisoRepository avisos = mock(AvisoRepository.class);
    private final PublicadorDeEventos eventos = mock(PublicadorDeEventos.class);
    private final Cuenta cuenta = cuenta(10, mesa(1));
    private final AvisosClienteService servicio = new AvisosClienteService(sesiones, cuentas, pedidos, avisos, eventos, () -> AHORA);

    @BeforeEach
    void preparar() {
        when(sesiones.buscarPorToken("cliente")).thenReturn(Optional.of(cliente(cuenta, true)));
        when(avisos.guardar(any())).thenAnswer(inv -> com.chitgo.pruebas.Entidades.conId(inv.getArgument(0), 50L));
    }

    @Test
    void llamarDesdeSesionLectoraCreaUnAvisoConLaCuentaYLaHoraYNotificaUnaVez() {
        when(sesiones.buscarPorToken("cliente")).thenReturn(Optional.of(cliente(cuenta, false)));
        var respuesta = servicio.llamarMozo("cliente");

        assertThat(respuesta.tipo()).isEqualTo("LLAMAR_MOZO");
        assertThat(respuesta.creadoEn()).isEqualTo(AHORA);
        var orden = inOrder(cuentas, avisos, eventos);
        orden.verify(cuentas).subirVersion(cuenta);
        orden.verify(avisos).guardar(argThat(a -> a.getCuentaId().equals(10L)));
        ArgumentCaptor<EventoTiempoReal> evento = ArgumentCaptor.forClass(EventoTiempoReal.class);
        orden.verify(eventos).aMozos(eq(1L), evento.capture());
        assertThat(evento.getValue().cuentaId()).isEqualTo(10L);
        assertThat(evento.getValue().detalle()).isEqualTo("LLAMAR_MOZO");
    }

    @Test
    void unLlamadoPendienteNoSeDuplica() {
        when(avisos.hayPendiente(10L, TipoAviso.LLAMAR_MOZO)).thenReturn(true);
        assertThatThrownBy(() -> servicio.llamarMozo("cliente"))
                .isInstanceOf(ReglaDeNegocioException.class).hasMessage("Ya avisamos al mozo");
        verify(avisos, never()).guardar(any());
        verifyNoInteractions(cuentas, eventos);
    }

    @Test
    void elConflictoContraOtroLlamadoOCierreNoCreaAvisosNiEventos() {
        doThrow(new ConflictoDeConcurrenciaException("cambió")).when(cuentas).subirVersion(cuenta);
        assertThatThrownBy(() -> servicio.llamarMozo("cliente")).isInstanceOf(ConflictoDeConcurrenciaException.class);
        verify(avisos, never()).guardar(any());
        verifyNoInteractions(eventos);
    }

    @Test
    void pedirLaCuentaGuardaSolicitadaYNotificaAMozoYCaja() {
        when(pedidos.listarDeLaCuenta(10L)).thenReturn(List.of(
                pedido(1, cuenta, EstadoPedido.SERVIDO), pedido(2, cuenta, EstadoPedido.RECHAZADO),
                pedido(3, cuenta, EstadoPedido.CANCELADO)));
        servicio.solicitarCuenta("cliente");
        assertThat(cuenta.getEstado()).isEqualTo(EstadoCuenta.SOLICITADA);
        assertThat(cuenta.getSolicitadaEn()).isEqualTo(AHORA);
        verify(cuentas).guardar(cuenta);
        verify(eventos).aCaja(eq(1L), argThat(e -> e.tipo() == TipoEvento.CUENTA_SOLICITADA && e.cuentaId().equals(10L)));
        verify(avisos).guardar(argThat(a -> a.getTipo() == TipoAviso.CUENTA_SOLICITADA));
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPedido.class, names = {"PENDIENTE_APROBACION", "APROBADO", "EN_PREPARACION", "LISTO"})
    void noSePideLaCuentaConPedidosSinServir(EstadoPedido estado) {
        when(pedidos.listarDeLaCuenta(10L)).thenReturn(List.of(pedido(1, cuenta, estado)));
        assertThatThrownBy(() -> servicio.solicitarCuenta("cliente")).isInstanceOf(ReglaDeNegocioException.class);
        assertThat(cuenta.getEstado()).isEqualTo(EstadoCuenta.ABIERTA);
        verifyNoInteractions(cuentas, eventos);
        verify(avisos, never()).guardar(any());
    }

    @Test
    void soloElIniciadorPuedePedirLaCuenta() {
        when(sesiones.buscarPorToken("cliente")).thenReturn(Optional.of(cliente(cuenta, false)));
        assertThatThrownBy(() -> servicio.solicitarCuenta("cliente")).isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(cuentas, pedidos, eventos);
    }

    @Test
    void pedirDosVecesLaCuentaDaConflicto() {
        cuenta.solicitar(AHORA);
        assertThatThrownBy(() -> servicio.solicitarCuenta("cliente")).hasMessage("La cuenta ya fue solicitada");
        verifyNoInteractions(cuentas, eventos);
    }

    @Test
    void unaCuentaReabiertaNoDuplicaElAvisoDeCuentaTodaviaPendiente() {
        cuenta.solicitar(AHORA.minusSeconds(60));
        cuenta.reabrirSiEstabaSolicitada();
        when(avisos.hayPendiente(10L, TipoAviso.CUENTA_SOLICITADA)).thenReturn(true);
        servicio.solicitarCuenta("cliente");
        assertThat(cuenta.getEstado()).isEqualTo(EstadoCuenta.SOLICITADA);
        verify(avisos, never()).guardar(any());
        verify(eventos, never()).aMozos(any(), any());
        verify(eventos).aCaja(eq(1L), any());
    }

    @Test
    void unaCuentaCerradaNoPermiteLlamarNiSolicitar() {
        cuenta.cerrar(40L, java.math.BigDecimal.ZERO, AHORA);
        assertThatThrownBy(() -> servicio.llamarMozo("cliente")).hasMessage("La cuenta ya está cerrada");
        assertThatThrownBy(() -> servicio.solicitarCuenta("cliente")).hasMessage("La cuenta ya está cerrada");
        verifyNoInteractions(cuentas, avisos, eventos);
    }

    @Test
    void tokenAusenteOInvalidoNoRevelaDatos() {
        assertThatThrownBy(() -> servicio.llamarMozo(null)).isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.solicitarCuenta("otra")).isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(cuentas, avisos, eventos);
    }
}
