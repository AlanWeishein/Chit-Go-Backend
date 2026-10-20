package com.chitgo.pedidos.aplicacion;

import static com.chitgo.pruebas.Entidades.con;
import static com.chitgo.pruebas.Entidades.conId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chitgo.auditoria.dominio.puertos.Auditor;
import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.ConflictoDeConcurrenciaException;
import com.chitgo.compartido.dominio.RelojDePrueba;
import com.chitgo.compartido.dominio.ReglaDeNegocioException;
import com.chitgo.menu.dominio.puertos.CartaRepository;
import com.chitgo.pedidos.aplicacion.dto.VistaDeCocinaDto;
import com.chitgo.pedidos.dominio.ClienteSesion;
import com.chitgo.pedidos.dominio.Cuenta;
import com.chitgo.pedidos.dominio.EstadoPedido;
import com.chitgo.pedidos.dominio.EventoTiempoReal;
import com.chitgo.pedidos.dominio.Mesa;
import com.chitgo.pedidos.dominio.Pedido;
import com.chitgo.pedidos.dominio.puertos.PedidoRepository;
import com.chitgo.pedidos.dominio.puertos.PublicadorDeEventos;
import com.chitgo.usuarios.aplicacion.AutorizadorDePermisos;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

class ColaDeCocinaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-20T20:10:00Z");
    private static final String TOKEN_CLIENTE = "token-de-A";
    private static final UsuarioAutenticado HUGO = new UsuarioAutenticado(9L, "COCINA", 1L, false, AHORA.plusSeconds(600));

    private final PedidoRepository pedidos = mock(PedidoRepository.class);
    private final PublicadorDeEventos eventos = mock(PublicadorDeEventos.class);
    private final Auditor auditor = mock(Auditor.class);
    private final CartaRepository carta = mock(CartaRepository.class);
    private final Mesa mesa = conId(new Mesa(1L, "4", "qr-4"), 3L);
    private final Cuenta cuenta = conId(new Cuenta(1L, mesa, 5L, AHORA), 7L);
    private final ClienteSesion sesion = conId(new ClienteSesion(1L, cuenta, TOKEN_CLIENTE, true), 10L);
    private ColaDeCocinaService servicio;

    /** Un autorizador que deja o no deja pasar a todos, sin tocar la base. */
    private static AutorizadorDePermisos autorizador(boolean permite) {
        return new AutorizadorDePermisos(null) {
            @Override
            public boolean tieneAlguno(UsuarioAutenticado sesion, String... codigos) {
                return permite;
            }

            @Override
            public void exigirAlguno(UsuarioAutenticado sesion, String... codigos) {
                if (!permite) {
                    throw new AccesoDenegadoException("No tenés permiso para hacer esto.");
                }
            }
        };
    }

    private void servicioCon(AutorizadorDePermisos autorizador) {
        servicio = new ColaDeCocinaService(pedidos, eventos, auditor, autorizador, new ArmadorDePedidos(carta),
                new RelojDePrueba(AHORA));
    }

    @BeforeEach
    void preparar() {
        servicioCon(autorizador(true));
        when(pedidos.guardar(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Pedido pedido(long id, EstadoPedido estado, boolean prioritario, String hora) {
        Instant llegada = Instant.parse("2026-10-20T" + hora + ":00Z");
        Pedido pedido = conId(new Pedido(1L, cuenta, sesion, "uuid-" + id, llegada.minusSeconds(30)), id);
        con(pedido, "estado", estado);
        con(pedido, "resueltoEn", llegada);
        con(pedido, "prioridadManual", prioritario);
        if (estado == EstadoPedido.EN_PREPARACION) {
            con(pedido, "enPreparacionEn", llegada.plusSeconds(60));
        }
        return pedido;
    }

    @Test
    void CP_24_02_la_cola_sale_con_el_prioritario_primero_y_en_columnas_por_estado() {
        when(pedidos.listarActivosDeCocina(1L)).thenReturn(List.of(
                pedido(1, EstadoPedido.APROBADO, false, "20:00"),
                pedido(2, EstadoPedido.APROBADO, true, "20:02"),
                pedido(3, EstadoPedido.APROBADO, false, "20:05"),
                pedido(4, EstadoPedido.EN_PREPARACION, false, "19:50")));

        VistaDeCocinaDto vista = servicio.obtener(HUGO);

        assertEquals(List.of(2L, 1L, 3L), vista.enCola().stream().map(p -> p.id()).toList());
        assertEquals(1, vista.enPreparacion().size());
        assertEquals(0, vista.listos().size());
        assertEquals("4", vista.enCola().get(0).mesa());
    }

    @Test
    void sin_permiso_no_se_ve_la_cola() {
        servicioCon(autorizador(false));
        assertThrows(AccesoDenegadoException.class, () -> servicio.obtener(HUGO));
    }

    @Test
    void el_gerente_dueno_no_tiene_local_y_no_opera_la_cocina() {
        UsuarioAutenticado gerente = new UsuarioAutenticado(1L, "GERENTE_DUENO", null, false, AHORA.plusSeconds(600));
        assertThrows(AccesoDenegadoException.class, () -> servicio.obtener(gerente));
    }

    @Test
    void CP_24_01_iniciar_preparacion_guarda_la_transicion_audita_y_avisa() {
        when(pedidos.buscarPorId(5L, 1L)).thenReturn(Optional.of(pedido(5, EstadoPedido.APROBADO, false, "20:00")));

        servicio.iniciarPreparacion(HUGO, 5L);

        verify(pedidos).guardar(any(Pedido.class));
        verify(auditor).registrar(eq(1L), eq(9L), eq("INICIAR_PREPARACION"), eq("PEDIDO"), eq(5L), any(), any());
        verify(eventos).aCocina(eq(1L), any(EventoTiempoReal.class));
        verify(eventos).aPantalla(eq(1L), any(EventoTiempoReal.class));
        verify(eventos).aCliente(eq(TOKEN_CLIENTE), any(EventoTiempoReal.class));
        verify(eventos, never()).aMozos(any(), any());
    }

    @Test
    void marcar_listo_avisa_tambien_al_mozo() {
        when(pedidos.buscarPorId(5L, 1L)).thenReturn(Optional.of(pedido(5, EstadoPedido.EN_PREPARACION, false, "20:00")));

        servicio.marcarListo(HUGO, 5L);

        verify(auditor).registrar(eq(1L), eq(9L), eq("MARCAR_PEDIDO_LISTO"), eq("PEDIDO"), eq(5L), any(), any());
        verify(eventos).aMozos(eq(1L), any(EventoTiempoReal.class));
    }

    @Test
    void CP_24_03_no_se_pasa_a_listo_un_pedido_que_sigue_aprobado() {
        when(pedidos.buscarPorId(5L, 1L)).thenReturn(Optional.of(pedido(5, EstadoPedido.APROBADO, false, "20:00")));

        assertThrows(ReglaDeNegocioException.class, () -> servicio.marcarListo(HUGO, 5L));

        verify(pedidos, never()).guardar(any());
        verify(auditor, never()).registrar(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void sin_permiso_no_se_cambia_el_estado() {
        servicioCon(autorizador(false));
        assertThrows(AccesoDenegadoException.class, () -> servicio.iniciarPreparacion(HUGO, 5L));
    }

    @Test
    void si_otro_se_adelanto_el_cambio_responde_409_y_no_audita() {
        when(pedidos.buscarPorId(5L, 1L)).thenReturn(Optional.of(pedido(5, EstadoPedido.APROBADO, false, "20:00")));
        when(pedidos.guardar(any(Pedido.class))).thenAnswer(inv -> {
            throw new ConflictoDeConcurrenciaException("otro lo cambió");
        });

        assertThrows(ReglaDeNegocioException.class, () -> servicio.iniciarPreparacion(HUGO, 5L));

        verify(auditor, never()).registrar(any(), any(), any(), any(), any(), any(), any());
    }
}
