package com.chitgo.pedidos.aplicacion;

import static com.chitgo.pruebas.Entidades.con;
import static com.chitgo.pruebas.Entidades.conId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import com.chitgo.auditoria.dominio.puertos.Auditor;
import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.ConflictoDeConcurrenciaException;
import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.compartido.dominio.ReglaDeNegocioException;
import com.chitgo.compartido.dominio.RelojDePrueba;
import com.chitgo.compartido.dominio.SolicitudInvalidaException;
import com.chitgo.pedidos.dominio.ClienteSesion;
import com.chitgo.pedidos.dominio.Cuenta;
import com.chitgo.pedidos.dominio.EstadoPedido;
import com.chitgo.pedidos.dominio.EventoTiempoReal;
import com.chitgo.pedidos.dominio.Mesa;
import com.chitgo.pedidos.dominio.MotivoRechazo;
import com.chitgo.pedidos.dominio.Pedido;
import com.chitgo.pedidos.dominio.TipoEvento;
import com.chitgo.pedidos.dominio.puertos.PedidoRepository;
import com.chitgo.pedidos.dominio.puertos.PublicadorDeEventos;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

class ResolverPedidoServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-20T20:02:00Z");
    private static final String TOKEN_CLIENTE = "token-de-A";
    private static final UsuarioAutenticado CARLA = new UsuarioAutenticado(6L, "CAJA", 1L, false, AHORA.plusSeconds(3600));

    private final PedidoRepository pedidos = mock(PedidoRepository.class);
    private final PublicadorDeEventos eventos = mock(PublicadorDeEventos.class);
    private final Auditor auditor = mock(Auditor.class);
    private final Mesa mesa = conId(new Mesa(1L, "1", "qr-1"), 3L);
    private final Cuenta cuenta = conId(new Cuenta(1L, mesa, 5L, AHORA), 7L);
    private final ClienteSesion sesion = conId(new ClienteSesion(1L, cuenta, TOKEN_CLIENTE, true), 10L);
    private ResolverPedidoService servicio;

    @BeforeEach
    void preparar() {
        servicio = new ResolverPedidoService(pedidos, eventos, auditor, new RelojDePrueba(AHORA),
                mock(PlatformTransactionManager.class));
        when(pedidos.guardar(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Pedido pedido(EstadoPedido estado) {
        Pedido pedido = conId(new Pedido(1L, cuenta, sesion, "uuid-5", AHORA.minusSeconds(120)), 5L);
        return con(pedido, "estado", estado);
    }

    /** Lo que devuelve cada búsqueda del pedido, en orden: la de la escritura y, si hubo choque, la de la lectura. */
    private void enLaBase(Pedido... lecturas) {
        Optional<Pedido> primera = Optional.of(lecturas[0]);
        @SuppressWarnings("unchecked")
        Optional<Pedido>[] siguientes = Arrays.stream(lecturas).skip(1).map(Optional::of).toArray(Optional[]::new);
        when(pedidos.buscarPorId(5L, 1L)).thenReturn(primera, siguientes);
    }

    private static EventoTiempoReal capturar(Consumer<ArgumentCaptor<EventoTiempoReal>> verificacion) {
        ArgumentCaptor<EventoTiempoReal> captor = ArgumentCaptor.forClass(EventoTiempoReal.class);
        verificacion.accept(captor);
        return captor.getValue();
    }

    @Test
    void CP_19_01_aprobar_guarda_quien_y_cuando_audita_y_avisa_a_cocina_al_cliente_a_caja_y_a_la_pantalla() {
        Pedido pedido = pedido(EstadoPedido.PENDIENTE_APROBACION);
        enLaBase(pedido);

        servicio.aprobar(CARLA, 5L);

        assertEquals(EstadoPedido.APROBADO, pedido.getEstado());
        assertEquals(6L, pedido.getResueltoPorUsuarioId());
        assertEquals(AHORA, pedido.getResueltoEn());
        verify(pedidos).guardar(pedido);
        verify(auditor).registrar(1L, 6L, "APROBAR_PEDIDO", "PEDIDO", 5L,
                Map.of("estado", "PENDIENTE_APROBACION"), Map.of("estado", "APROBADO"));

        EventoTiempoReal aCocina = capturar(c -> verify(eventos).aCocina(eq(1L), c.capture()));
        assertEquals(TipoEvento.PEDIDO_APROBADO, aCocina.tipo());
        EventoTiempoReal alCliente = capturar(c -> verify(eventos).aCliente(eq(TOKEN_CLIENTE), c.capture()));
        assertEquals(TipoEvento.APROBADO, alCliente.tipo());
        EventoTiempoReal aPantalla = capturar(c -> verify(eventos).aPantalla(eq(1L), c.capture()));
        assertEquals(TipoEvento.APROBADO, aPantalla.tipo());
        EventoTiempoReal aCaja = capturar(c -> verify(eventos).aCaja(eq(1L), c.capture()));
        assertEquals(TipoEvento.PEDIDO_RESUELTO, aCaja.tipo());
        assertEquals("APROBADO", aCaja.estado());
        assertEquals(5L, aCaja.pedidoId());
        assertEquals(3L, aCaja.mesaId());
        verify(eventos, never()).aMozos(any(), any());
    }

    @Test
    void CP_19_03_rechazar_audita_el_motivo_avisa_al_cliente_y_a_caja_y_no_va_a_cocina() {
        Pedido pedido = pedido(EstadoPedido.PENDIENTE_APROBACION);
        enLaBase(pedido);

        servicio.rechazar(CARLA, 5L, MotivoRechazo.MESA_NO_OCUPADA);

        assertEquals(EstadoPedido.RECHAZADO, pedido.getEstado());
        assertEquals(MotivoRechazo.MESA_NO_OCUPADA, pedido.getMotivoRechazo());
        verify(auditor).registrar(1L, 6L, "RECHAZAR_PEDIDO", "PEDIDO", 5L,
                Map.of("estado", "PENDIENTE_APROBACION"), Map.of("estado", "RECHAZADO", "motivo", "MESA_NO_OCUPADA"));

        EventoTiempoReal alCliente = capturar(c -> verify(eventos).aCliente(eq(TOKEN_CLIENTE), c.capture()));
        assertEquals(TipoEvento.RECHAZADO, alCliente.tipo());
        assertEquals("MESA_NO_OCUPADA", alCliente.detalle());
        EventoTiempoReal aCaja = capturar(c -> verify(eventos).aCaja(eq(1L), c.capture()));
        assertEquals(TipoEvento.PEDIDO_RESUELTO, aCaja.tipo());
        assertEquals("RECHAZADO", aCaja.estado());
        verify(eventos, never()).aCocina(any(), any());
        verify(eventos, never()).aPantalla(any(), any());
    }

    @Test
    void CP_19_04_rechazar_sin_motivo_es_400_y_no_guarda_ni_audita() {
        Pedido pedido = pedido(EstadoPedido.PENDIENTE_APROBACION);
        enLaBase(pedido);

        assertThrows(SolicitudInvalidaException.class, () -> servicio.rechazar(CARLA, 5L, null));

        assertEquals(EstadoPedido.PENDIENTE_APROBACION, pedido.getEstado());
        verify(pedidos, never()).guardar(any());
        verifyNoInteractions(auditor, eventos);
    }

    @Test
    void CP_19_05_si_el_cliente_ya_lo_cancelo_responde_ya_fue_cancelado() {
        enLaBase(pedido(EstadoPedido.CANCELADO));

        ReglaDeNegocioException e = assertThrows(ReglaDeNegocioException.class, () -> servicio.aprobar(CARLA, 5L));

        assertEquals("El pedido ya fue cancelado", e.getMessage());
        verify(pedidos, never()).guardar(any());
        verifyNoInteractions(auditor, eventos);
    }

    @Test
    void si_otra_caja_ya_lo_resolvio_responde_ya_fue_resuelto() {
        enLaBase(pedido(EstadoPedido.RECHAZADO));

        ReglaDeNegocioException e = assertThrows(ReglaDeNegocioException.class, () -> servicio.aprobar(CARLA, 5L));

        assertEquals("El pedido ya fue resuelto", e.getMessage());
        verifyNoInteractions(auditor, eventos);
    }

    @Test
    void CP_19_06_si_otra_caja_gano_por_un_instante_el_choque_dice_ya_fue_resuelto_y_no_audita_ni_avisa() {
        enLaBase(pedido(EstadoPedido.PENDIENTE_APROBACION), pedido(EstadoPedido.APROBADO));
        when(pedidos.guardar(any(Pedido.class))).thenThrow(new ConflictoDeConcurrenciaException("otro lo cambió"));

        ReglaDeNegocioException e = assertThrows(ReglaDeNegocioException.class,
                () -> servicio.rechazar(CARLA, 5L, MotivoRechazo.NO_DISPONIBLE));

        assertEquals("El pedido ya fue resuelto", e.getMessage());
        verifyNoInteractions(auditor, eventos);
    }

    @Test
    void si_el_cliente_cancelo_en_el_mismo_instante_el_choque_dice_ya_fue_cancelado() {
        enLaBase(pedido(EstadoPedido.PENDIENTE_APROBACION), pedido(EstadoPedido.CANCELADO));
        when(pedidos.guardar(any(Pedido.class))).thenThrow(new ConflictoDeConcurrenciaException("otro lo cambió"));

        ReglaDeNegocioException e = assertThrows(ReglaDeNegocioException.class, () -> servicio.aprobar(CARLA, 5L));

        assertEquals("El pedido ya fue cancelado", e.getMessage());
        verifyNoInteractions(auditor, eventos);
    }

    @Test
    void si_despues_del_choque_sigue_pendiente_sale_el_409_del_choque() {
        enLaBase(pedido(EstadoPedido.PENDIENTE_APROBACION), pedido(EstadoPedido.PENDIENTE_APROBACION));
        ConflictoDeConcurrenciaException choque = new ConflictoDeConcurrenciaException("otro lo cambió");
        when(pedidos.guardar(any(Pedido.class))).thenThrow(choque);

        ReglaDeNegocioException e = assertThrows(ReglaDeNegocioException.class, () -> servicio.aprobar(CARLA, 5L));

        assertSame(choque, e);
    }

    @Test
    void CP_19_08_un_pedido_de_otro_local_da_404_como_uno_que_no_existe() {
        when(pedidos.buscarPorId(5L, 1L)).thenReturn(Optional.empty());

        NoEncontradoException e = assertThrows(NoEncontradoException.class, () -> servicio.aprobar(CARLA, 5L));

        assertEquals("El pedido no existe", e.getMessage());
        verifyNoInteractions(auditor, eventos);
    }

    @Test
    void el_gerente_dueno_no_tiene_local_y_no_opera_la_caja() {
        UsuarioAutenticado gerente = new UsuarioAutenticado(1L, "GERENTE_DUENO", null, false, AHORA.plusSeconds(600));

        assertThrows(AccesoDenegadoException.class, () -> servicio.aprobar(gerente, 5L));
        assertThrows(AccesoDenegadoException.class,
                () -> servicio.rechazar(gerente, 5L, MotivoRechazo.NO_DISPONIBLE));
        verifyNoInteractions(pedidos, auditor, eventos);
    }
}
