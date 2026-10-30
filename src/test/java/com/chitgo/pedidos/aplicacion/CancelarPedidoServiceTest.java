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
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.ConflictoDeConcurrenciaException;
import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.compartido.dominio.RelojDePrueba;
import com.chitgo.compartido.dominio.ReglaDeNegocioException;
import com.chitgo.menu.dominio.puertos.CartaRepository;
import com.chitgo.pedidos.aplicacion.dto.PedidoDto;
import com.chitgo.pedidos.dominio.ClienteSesion;
import com.chitgo.pedidos.dominio.Cuenta;
import com.chitgo.pedidos.dominio.EstadoPedido;
import com.chitgo.pedidos.dominio.EventoTiempoReal;
import com.chitgo.pedidos.dominio.Mesa;
import com.chitgo.pedidos.dominio.Pedido;
import com.chitgo.pedidos.dominio.puertos.ClienteSesionRepository;
import com.chitgo.pedidos.dominio.puertos.PedidoRepository;
import com.chitgo.pedidos.dominio.puertos.PublicadorDeEventos;

class CancelarPedidoServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-20T20:00:00Z");
    private static final String TOKEN = "token-de-A";

    private final ClienteSesionRepository sesiones = mock(ClienteSesionRepository.class);
    private final PedidoRepository pedidos = mock(PedidoRepository.class);
    private final PublicadorDeEventos eventos = mock(PublicadorDeEventos.class);
    private final CartaRepository carta = mock(CartaRepository.class);
    private final Mesa mesa = conId(new Mesa(1L, "1", "qr-1"), 3L);
    private final Cuenta cuenta = conId(new Cuenta(1L, mesa, 5L, AHORA), 7L);
    private final ClienteSesion sesion = conId(new ClienteSesion(1L, cuenta, TOKEN, true), 10L);
    private CancelarPedidoService servicio;

    @BeforeEach
    void preparar() {
        servicio = new CancelarPedidoService(sesiones, pedidos, eventos, new ArmadorDePedidos(carta),
                new RelojDePrueba(AHORA));
        when(sesiones.buscarPorToken(TOKEN)).thenReturn(Optional.of(sesion));
        when(pedidos.guardar(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Pedido pedido(EstadoPedido estado, ClienteSesion deLaSesion) {
        Pedido pedido = conId(new Pedido(1L, cuenta, deLaSesion, UUID.randomUUID().toString(), AHORA), 5L);
        return con(pedido, "estado", estado);
    }

    @Test
    void CP_05_01_el_cliente_cancela_su_pedido_pendiente_y_se_avisa_a_caja() {
        when(pedidos.buscarPorId(5L, 1L)).thenReturn(Optional.of(pedido(EstadoPedido.PENDIENTE_APROBACION, sesion)));

        PedidoDto cancelado = servicio.cancelar(TOKEN, 5L);

        assertEquals("CANCELADO", cancelado.estado());
        verify(pedidos).guardar(any(Pedido.class));
        verify(eventos).aCaja(eq(1L), any(EventoTiempoReal.class));
    }

    @Test
    void no_se_cancela_un_pedido_de_otra_sesion() {
        ClienteSesion otra = conId(new ClienteSesion(1L, cuenta, "token-de-B", false), 11L);
        when(pedidos.buscarPorId(5L, 1L)).thenReturn(Optional.of(pedido(EstadoPedido.PENDIENTE_APROBACION, otra)));
        assertThrows(AccesoDenegadoException.class, () -> servicio.cancelar(TOKEN, 5L));
        verify(pedidos, never()).guardar(any());
    }

    @Test
    void un_pedido_que_no_existe_da_404() {
        when(pedidos.buscarPorId(99L, 1L)).thenReturn(Optional.empty());
        assertThrows(NoEncontradoException.class, () -> servicio.cancelar(TOKEN, 99L));
    }

    @Test
    void un_pedido_ya_aprobado_no_se_cancela_y_el_mensaje_es_el_de_la_historia() {
        when(pedidos.buscarPorId(5L, 1L)).thenReturn(Optional.of(pedido(EstadoPedido.APROBADO, sesion)));
        ReglaDeNegocioException e = assertThrows(ReglaDeNegocioException.class, () -> servicio.cancelar(TOKEN, 5L));
        assertEquals("El pedido ya fue aprobado. Si necesitás cambiarlo, llamá al mozo.", e.getMessage());
        verify(pedidos, never()).guardar(any());
    }

    @Test
    void si_caja_aprueba_justo_antes_gana_caja_y_el_cliente_recibe_el_aviso() {
        when(pedidos.buscarPorId(5L, 1L)).thenReturn(Optional.of(pedido(EstadoPedido.PENDIENTE_APROBACION, sesion)));
        when(pedidos.guardar(any(Pedido.class))).thenAnswer(inv -> {
            throw new ConflictoDeConcurrenciaException("otro lo cambió");
        });

        ReglaDeNegocioException e = assertThrows(ReglaDeNegocioException.class, () -> servicio.cancelar(TOKEN, 5L));

        assertEquals(CancelarPedidoService.YA_CAMBIO, e.getMessage());
        verify(eventos, never()).aCaja(any(), any());
    }
}
