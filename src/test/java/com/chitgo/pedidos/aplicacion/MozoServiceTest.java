package com.chitgo.pedidos.aplicacion;

import static com.chitgo.pedidos.aplicacion.DatosSprint2.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import com.chitgo.auditoria.dominio.puertos.Auditor;
import com.chitgo.compartido.dominio.*;
import com.chitgo.menu.dominio.puertos.CartaRepository;
import com.chitgo.pedidos.dominio.*;
import com.chitgo.pedidos.dominio.puertos.*;
import com.chitgo.usuarios.dominio.puertos.AsignacionesDeMozo;

class MozoServiceTest {
    private final MesaRepository mesas = mock(MesaRepository.class);
    private final CuentaRepository cuentas = mock(CuentaRepository.class);
    private final PedidoRepository pedidos = mock(PedidoRepository.class);
    private final AvisoRepository avisos = mock(AvisoRepository.class);
    private final AsignacionesDeMozo asignaciones = mock(AsignacionesDeMozo.class);
    private final PublicadorDeEventos eventos = mock(PublicadorDeEventos.class);
    private final Auditor auditor = mock(Auditor.class);
    private final Cuenta cuenta = cuenta(10, mesa(1));
    private final MozoService servicio = new MozoService(mesas, cuentas, pedidos, avisos, asignaciones,
            new ArmadorDePedidos(mock(CartaRepository.class)), eventos, auditor, () -> AHORA);

    @BeforeEach
    void preparar() { when(asignaciones.mesasDe(30L, 1L)).thenReturn(Set.of(1L, 2L)); }

    @Test
    void propiasIncluyeMesasLibresYElSalonCompletoMarcaLasAjenas() {
        when(mesas.listarDelLocal(1L)).thenReturn(List.of(cuenta.getMesa(), mesa(2), mesa(3)));
        when(cuentas.listarDelLocal(eq(1L), any())).thenReturn(List.of(cuenta));
        when(pedidos.listarDeLasCuentas(List.of(10L))).thenReturn(List.of(pedido(5, cuenta, EstadoPedido.LISTO)));
        var propias = servicio.mesas(JUAN, false);
        assertThat(propias).extracting(m -> m.id()).containsExactly(1L, 2L);
        assertThat(propias.get(1).estado()).isEqualTo("LIBRE");
        assertThat(propias.get(0).pedidos()).extracting(p -> p.id()).containsExactly(5L);
        var salon = servicio.mesas(JUAN, true);
        assertThat(salon).hasSize(3);
        assertThat(salon.get(2).asignada()).isFalse();
        assertThat(salon.get(2).cuentaId()).isNull();
    }

    @Test
    void losAvisosPendientesNoMezclanMesasNiCuentasAnteriores() {
        Aviso actual = aviso(1, cuenta, TipoAviso.LLAMAR_MOZO);
        Aviso viejo = aviso(2, cuenta(9, cuenta.getMesa()), TipoAviso.LLAMAR_MOZO);
        Aviso ajeno = aviso(3, cuenta(11, mesa(3)), TipoAviso.CUENTA_SOLICITADA);
        when(cuentas.listarDelLocal(eq(1L), any())).thenReturn(List.of(cuenta));
        when(avisos.listarDelLocal(1L, true)).thenReturn(List.of(actual, viejo, ajeno));
        assertThat(servicio.avisos(JUAN, true)).extracting(a -> a.id()).containsExactly(1L);
    }

    @Test
    void servirGuardaElMozoYLaHoraYAvisaAClienteCocinaYLosOtrosMozos() {
        Pedido pedido = pedido(5, cuenta, EstadoPedido.LISTO);
        when(pedidos.buscarPorId(5L, 1L)).thenReturn(Optional.of(pedido));
        servicio.servir(JUAN, 5L);
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.SERVIDO);
        assertThat(pedido.getServidoPorUsuarioId()).isEqualTo(30L);
        assertThat(pedido.getServidoEn()).isEqualTo(AHORA);
        var orden = inOrder(cuentas, pedidos, auditor);
        orden.verify(cuentas).subirVersion(cuenta);
        orden.verify(pedidos).guardar(pedido);
        orden.verify(auditor).registrar(eq(1L), eq(30L), eq("SERVIR_PEDIDO"), eq("PEDIDO"), eq(5L), any(), any());
        verify(eventos).aCliente(eq("cliente"), argThat(e -> e.tipo() == TipoEvento.SERVIDO));
        verify(eventos).aCocina(eq(1L), any());
        verify(eventos).aMozos(eq(1L), any());
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPedido.class, mode = EnumSource.Mode.EXCLUDE, names = "LISTO")
    void noSeSirveUnPedidoQueNoEstaListo(EstadoPedido estado) {
        when(pedidos.buscarPorId(5L, 1L)).thenReturn(Optional.of(pedido(5, cuenta, estado)));
        assertThatThrownBy(() -> servicio.servir(JUAN, 5L)).isInstanceOf(ReglaDeNegocioException.class);
        verifyNoInteractions(cuentas, auditor, eventos);
    }

    @Test
    void noSeSirveNiAtiendeUnaMesaAjenaAunqueSePuedaVerElSalon() {
        Cuenta ajena = cuenta(11, mesa(3));
        when(pedidos.buscarPorId(5L, 1L)).thenReturn(Optional.of(pedido(5, ajena, EstadoPedido.LISTO)));
        when(avisos.buscarPorId(1L, 1L)).thenReturn(Optional.of(aviso(1, ajena, TipoAviso.LLAMAR_MOZO)));
        assertThatThrownBy(() -> servicio.servir(JUAN, 5L)).isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.atender(JUAN, 1L)).isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(cuentas, auditor, eventos);
    }

    @Test
    void otroLocalSeBuscaConElLocalDeLaSesionYRespondeNoEncontrado() {
        assertThatThrownBy(() -> servicio.servir(JUAN, 999L)).isInstanceOf(NoEncontradoException.class);
        assertThatThrownBy(() -> servicio.atender(JUAN, 999L)).isInstanceOf(NoEncontradoException.class);
        verify(pedidos).buscarPorId(999L, 1L);
        verify(avisos).buscarPorId(999L, 1L);
    }

    @Test
    void siElCierreGanoNoSeSirveNiSeNotifica() {
        Pedido pedido = pedido(5, cuenta, EstadoPedido.LISTO);
        when(pedidos.buscarPorId(5L, 1L)).thenReturn(Optional.of(pedido));
        doThrow(new ConflictoDeConcurrenciaException("cerrada")).when(cuentas).subirVersion(cuenta);
        assertThatThrownBy(() -> servicio.servir(JUAN, 5L)).isInstanceOf(ConflictoDeConcurrenciaException.class);
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.LISTO);
        verify(pedidos, never()).guardar(any());
        verifyNoInteractions(auditor, eventos);
    }

    @Test
    void atenderRegistraElResponsableYNoSePuedeRepetir() {
        Aviso aviso = aviso(1, cuenta, TipoAviso.LLAMAR_MOZO);
        when(avisos.buscarPorId(1L, 1L)).thenReturn(Optional.of(aviso));
        when(cuentas.buscarNoCerradaDeMesa(1L)).thenReturn(Optional.of(cuenta));
        servicio.atender(JUAN, 1L);
        assertThat(aviso.getAtendidoPorUsuarioId()).isEqualTo(30L);
        assertThat(aviso.getAtendidoEn()).isEqualTo(AHORA);
        verify(auditor).registrar(eq(1L), eq(30L), eq("ATENDER_AVISO"), eq("AVISO"), eq(1L), any(), any());
        verify(eventos).aMozos(eq(1L), argThat(e -> e.tipo() == TipoEvento.AVISO_ATENDIDO));
        assertThatThrownBy(() -> servicio.atender(JUAN, 1L)).hasMessage("El aviso ya fue atendido");
        verify(avisos, times(1)).guardar(aviso);
    }

    @Test
    void unAvisoViejoNoSeAtiendeSobreUnaCuentaNuevaDeLaMismaMesa() {
        when(avisos.buscarPorId(1L, 1L)).thenReturn(Optional.of(aviso(1, cuenta, TipoAviso.LLAMAR_MOZO)));
        when(cuentas.buscarNoCerradaDeMesa(1L)).thenReturn(Optional.of(cuenta(20, cuenta.getMesa())));
        assertThatThrownBy(() -> servicio.atender(JUAN, 1L)).hasMessage("La cuenta del aviso ya está cerrada");
        verify(avisos, never()).guardar(any());
        verifyNoInteractions(auditor, eventos);
    }
}
