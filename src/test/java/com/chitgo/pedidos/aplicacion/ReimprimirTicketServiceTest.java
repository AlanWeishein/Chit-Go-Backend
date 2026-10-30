package com.chitgo.pedidos.aplicacion;

import static com.chitgo.pruebas.Entidades.con;
import static com.chitgo.pruebas.Entidades.conId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import com.chitgo.auditoria.dominio.puertos.Auditor;
import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.compartido.dominio.RelojDePrueba;
import com.chitgo.menu.dominio.CategoriaMenu;
import com.chitgo.menu.dominio.Plato;
import com.chitgo.menu.dominio.puertos.CartaRepository;
import com.chitgo.pedidos.aplicacion.dto.ItemDeTicketDto;
import com.chitgo.pedidos.aplicacion.dto.TicketDto;
import com.chitgo.pedidos.dominio.ClienteSesion;
import com.chitgo.pedidos.dominio.Cuenta;
import com.chitgo.pedidos.dominio.EstadoCuenta;
import com.chitgo.pedidos.dominio.EstadoPedido;
import com.chitgo.pedidos.dominio.ItemPedido;
import com.chitgo.pedidos.dominio.Mesa;
import com.chitgo.pedidos.dominio.Pedido;
import com.chitgo.pedidos.dominio.Ticket;
import com.chitgo.pedidos.dominio.puertos.CuentaRepository;
import com.chitgo.pedidos.dominio.puertos.PedidoRepository;
import com.chitgo.pedidos.dominio.puertos.PublicadorDeEventos;
import com.chitgo.pedidos.dominio.puertos.TicketRepository;
import com.chitgo.usuarios.dominio.Local;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;
import com.chitgo.usuarios.dominio.puertos.LocalRepository;

class ReimprimirTicketServiceTest {

    private static final Instant CIERRE = Instant.parse("2026-10-30T20:00:00Z");
    private static final UsuarioAutenticado CARLA = new UsuarioAutenticado(6L, "CAJA", 1L, false, CIERRE.plusSeconds(3600));

    private final CuentaRepository cuentas = mock(CuentaRepository.class);
    private final PedidoRepository pedidos = mock(PedidoRepository.class);
    private final TicketRepository tickets = mock(TicketRepository.class);
    private final CartaRepository carta = mock(CartaRepository.class);
    private final LocalRepository locales = mock(LocalRepository.class);

    private final Plato milanesa = plato(1, "Milanesa napolitana", "9500.00");
    private final Plato flan = plato(2, "Flan casero", "4000.00");
    private final Mesa mesa = conId(new Mesa(1L, "1", "qr-1"), 3L);
    private final Cuenta cuenta = conId(new Cuenta(1L, mesa, 5L, CIERRE.minusSeconds(3600)), 7L);
    private final ClienteSesion sesion = conId(new ClienteSesion(1L, cuenta, "token-de-A", true), 10L);
    private ArmadorDeTicket armadorDeTicket;
    private ReimprimirTicketService servicio;

    private static Plato plato(long id, String nombre, String precio) {
        CategoriaMenu categoria = conId(new CategoriaMenu(1L, "Principales", (short) 1), 1L);
        return conId(new Plato(1L, categoria, nombre, null, new BigDecimal(precio), null), id);
    }

    @BeforeEach
    void preparar() {
        ArmadorDePedidos armador = new ArmadorDePedidos(carta);
        armadorDeTicket = new ArmadorDeTicket(locales, armador);
        servicio = new ReimprimirTicketService(cuentas, pedidos, tickets, armadorDeTicket);
        when(cuentas.buscarPorId(7L, 1L)).thenReturn(Optional.of(cuenta));
        when(locales.buscarPorId(1L)).thenReturn(Optional.of(
                conId(new Local(2L, "La Esquina", "Calle 1", "token-staff"), 1L)));
        when(carta.buscarPorIds(eq(1L), any())).thenReturn(List.of(milanesa, flan));
    }

    private Pedido pedido(long id, EstadoPedido estado, Plato plato, int cantidad, String precio) {
        Pedido pedido = con(conId(new Pedido(1L, cuenta, sesion, "uuid-" + id, CIERRE.minusSeconds(600)), id),
                "estado", estado);
        new ItemPedido(1L, pedido, plato.getId(), (short) cantidad, new BigDecimal(precio), null);
        return pedido;
    }

    /** Lo que deja el cierre de CP-22-01: la cuenta Cerrada y su ticket 99 con $ 23.000. */
    private void cerrada(Pedido... deLaCuenta) {
        cuenta.cerrar(6L, new BigDecimal("23000.00"), CIERRE);
        when(pedidos.listarDeLaCuenta(7L)).thenReturn(List.of(deLaCuenta));
        when(tickets.buscarDeLaCuenta(7L)).thenReturn(Optional.of(
                conId(new Ticket(1L, cuenta, CIERRE, new BigDecimal("23000.00")), 99L)));
    }

    @Test
    void el_ticket_de_una_cuenta_cerrada_sale_con_el_numero_la_hora_y_el_total_de_su_fila() {
        cerrada(pedido(40, EstadoPedido.SERVIDO, milanesa, 1, "9500.00"),
                pedido(41, EstadoPedido.SERVIDO, milanesa, 1, "9500.00"),
                pedido(42, EstadoPedido.SERVIDO, flan, 1, "4000.00"),
                pedido(43, EstadoPedido.RECHAZADO, flan, 2, "4000.00"),
                pedido(44, EstadoPedido.CANCELADO, milanesa, 1, "9500.00"));

        TicketDto ticket = servicio.reimprimir(CARLA, 7L);

        assertEquals(new TicketDto(99L, "La Esquina", "1", CIERRE, List.of(
                new ItemDeTicketDto("Milanesa napolitana", 2, new BigDecimal("9500.00"), new BigDecimal("19000.00")),
                new ItemDeTicketDto("Flan casero", 1, new BigDecimal("4000.00"), new BigDecimal("4000.00"))),
                new BigDecimal("23000.00"), "X", "DOCUMENTO NO VÁLIDO COMO FACTURA"), ticket);
    }

    @Test
    void CP_22_03_reimpreso_usa_el_precio_del_pedido_aunque_el_plato_haya_subido() {
        when(carta.buscarPorIds(eq(1L), any())).thenReturn(List.of(plato(1, "Milanesa napolitana", "10500.00")));
        cerrada(pedido(40, EstadoPedido.SERVIDO, milanesa, 1, "9500.00"));

        TicketDto ticket = servicio.reimprimir(CARLA, 7L);

        assertEquals(new BigDecimal("9500.00"), ticket.items().get(0).precioUnitario());
    }

    @Test
    void reimprimir_da_el_mismo_ticket_que_devolvio_el_cierre() {
        Pedido servido = pedido(40, EstadoPedido.SERVIDO, milanesa, 2, "9500.00");
        Pedido postre = pedido(42, EstadoPedido.SERVIDO, flan, 1, "4000.00");
        Pedido enPreparacion = pedido(41, EstadoPedido.EN_PREPARACION, flan, 1, "4000.00");
        con(cuenta, "estado", EstadoCuenta.SOLICITADA);
        when(pedidos.listarDeLaCuenta(7L)).thenReturn(List.of(servido, enPreparacion, postre));
        when(cuentas.guardar(any(Cuenta.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pedidos.guardar(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tickets.guardar(any(Ticket.class))).thenAnswer(inv -> conId(inv.getArgument(0), 99L));
        CerrarCuentaService cierre = new CerrarCuentaService(cuentas, pedidos, tickets, new ArmadorDePedidos(carta),
                armadorDeTicket, mock(PublicadorDeEventos.class), mock(Auditor.class), new RelojDePrueba(CIERRE));

        TicketDto alCerrar = cierre.cerrar(CARLA, 7L, true, List.of(41L));
        ArgumentCaptor<Ticket> guardado = ArgumentCaptor.forClass(Ticket.class);
        verify(tickets).guardar(guardado.capture());
        when(tickets.buscarDeLaCuenta(7L)).thenReturn(Optional.of(guardado.getValue()));

        assertEquals(alCerrar, servicio.reimprimir(CARLA, 7L));
    }

    @ParameterizedTest
    @EnumSource(value = EstadoCuenta.class, names = { "ABIERTA", "SOLICITADA" })
    void una_cuenta_que_no_esta_cerrada_todavia_no_tiene_ticket(EstadoCuenta estado) {
        con(cuenta, "estado", estado);

        NoEncontradoException e = assertThrows(NoEncontradoException.class, () -> servicio.reimprimir(CARLA, 7L));

        assertEquals("Esta cuenta todavía no tiene ticket", e.getMessage());
        verifyNoInteractions(tickets);
        verify(pedidos, never()).listarDeLaCuenta(anyLong());
    }

    @Test
    void una_cuenta_de_otro_local_da_el_mismo_404_que_una_que_no_existe() {
        when(cuentas.buscarPorId(8L, 1L)).thenReturn(Optional.empty());

        NoEncontradoException deOtroLocal = assertThrows(NoEncontradoException.class,
                () -> servicio.reimprimir(CARLA, 8L));

        assertEquals("La cuenta no existe", deOtroLocal.getMessage());
        verifyNoInteractions(tickets, pedidos);
    }

    @Test
    void el_gerente_dueno_no_tiene_local_y_no_opera_la_caja() {
        UsuarioAutenticado gerente = new UsuarioAutenticado(1L, "GERENTE_DUENO", null, false, CIERRE.plusSeconds(600));

        assertThrows(AccesoDenegadoException.class, () -> servicio.reimprimir(gerente, 7L));
        verifyNoInteractions(cuentas, tickets, pedidos);
    }

    @Test
    void una_cuenta_cerrada_sin_ticket_es_un_dato_roto_y_no_un_404() {
        cuenta.cerrar(6L, new BigDecimal("0.00"), CIERRE);
        when(tickets.buscarDeLaCuenta(7L)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> servicio.reimprimir(CARLA, 7L));
    }
}
