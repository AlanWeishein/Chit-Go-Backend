package com.chitgo.pedidos.aplicacion;

import static com.chitgo.pruebas.Entidades.con;
import static com.chitgo.pruebas.Entidades.conId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.chitgo.auditoria.dominio.puertos.Auditor;
import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.ConflictoDeConcurrenciaException;
import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.compartido.dominio.ReglaDeNegocioException;
import com.chitgo.compartido.dominio.RelojDePrueba;
import com.chitgo.menu.dominio.CategoriaMenu;
import com.chitgo.menu.dominio.Plato;
import com.chitgo.menu.dominio.puertos.CartaRepository;
import com.chitgo.pedidos.aplicacion.dto.ItemDeTicketDto;
import com.chitgo.pedidos.aplicacion.dto.PedidoSinServirDto;
import com.chitgo.pedidos.aplicacion.dto.PlatoSinServirDto;
import com.chitgo.pedidos.aplicacion.dto.TicketDto;
import com.chitgo.pedidos.dominio.ClienteSesion;
import com.chitgo.pedidos.dominio.Cuenta;
import com.chitgo.pedidos.dominio.EstadoCuenta;
import com.chitgo.pedidos.dominio.EstadoPedido;
import com.chitgo.pedidos.dominio.EventoTiempoReal;
import com.chitgo.pedidos.dominio.ItemPedido;
import com.chitgo.pedidos.dominio.Mesa;
import com.chitgo.pedidos.dominio.MotivoDeConfirmacion;
import com.chitgo.pedidos.dominio.Pedido;
import com.chitgo.pedidos.dominio.Ticket;
import com.chitgo.pedidos.dominio.TipoEvento;
import com.chitgo.pedidos.dominio.puertos.CuentaRepository;
import com.chitgo.pedidos.dominio.puertos.PedidoRepository;
import com.chitgo.pedidos.dominio.puertos.PublicadorDeEventos;
import com.chitgo.pedidos.dominio.puertos.TicketRepository;
import com.chitgo.usuarios.dominio.Local;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;
import com.chitgo.usuarios.dominio.puertos.LocalRepository;

class CerrarCuentaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-30T20:00:00Z");
    private static final UsuarioAutenticado CARLA = new UsuarioAutenticado(6L, "CAJA", 1L, false, AHORA.plusSeconds(3600));
    private static final String MENSAJE_NADIE = "Esta cuenta no la pidió nadie. ¿La cerramos igual?";
    private static final String MENSAJE_SIN_SERVIR = "Hay pedidos sin servir. ¿Estás seguro de que querés cerrar la cuenta?";

    private record Renglon(Plato plato, int cantidad, String precio) {
    }

    private final CuentaRepository cuentas = mock(CuentaRepository.class);
    private final PedidoRepository pedidos = mock(PedidoRepository.class);
    private final TicketRepository tickets = mock(TicketRepository.class);
    private final CartaRepository carta = mock(CartaRepository.class);
    private final LocalRepository locales = mock(LocalRepository.class);
    private final PublicadorDeEventos eventos = mock(PublicadorDeEventos.class);
    private final Auditor auditor = mock(Auditor.class);

    private final Plato milanesa = plato(1, "Milanesa napolitana", "9500.00");
    private final Plato flan = plato(2, "Flan casero", "4000.00");
    private final Plato ensalada = plato(3, "Ensalada mixta", "6000.00");
    private final Mesa mesa = conId(new Mesa(1L, "1", "qr-1"), 3L);
    private final Cuenta cuenta = con(conId(new Cuenta(1L, mesa, 5L, AHORA.minusSeconds(3600)), 7L), "estado",
            EstadoCuenta.SOLICITADA);
    private final ClienteSesion sesionA = conId(new ClienteSesion(1L, cuenta, "token-de-A", true), 10L);
    private final ClienteSesion sesionB = conId(new ClienteSesion(1L, cuenta, "token-de-B", false), 11L);
    private CerrarCuentaService servicio;

    private static Plato plato(long id, String nombre, String precio) {
        CategoriaMenu categoria = conId(new CategoriaMenu(1L, "Principales", (short) 1), 1L);
        return conId(new Plato(1L, categoria, nombre, null, new BigDecimal(precio), null), id);
    }

    @BeforeEach
    void preparar() {
        ArmadorDePedidos armador = new ArmadorDePedidos(carta);
        servicio = new CerrarCuentaService(cuentas, pedidos, tickets, armador, new ArmadorDeTicket(locales, armador),
                eventos, auditor, new RelojDePrueba(AHORA));
        when(cuentas.buscarPorId(7L, 1L)).thenReturn(Optional.of(cuenta));
        when(cuentas.guardar(any(Cuenta.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pedidos.guardar(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tickets.guardar(any(Ticket.class))).thenAnswer(inv -> conId(inv.getArgument(0), 99L));
        when(locales.buscarPorId(1L)).thenReturn(Optional.of(
                conId(new Local(2L, "La Esquina", "Calle 1", "token-staff"), 1L)));
        when(carta.buscarPorIds(eq(1L), any())).thenReturn(List.of(milanesa, flan, ensalada));
    }

    private Renglon x(Plato plato, int cantidad, String precio) {
        return new Renglon(plato, cantidad, precio);
    }

    private Pedido pedido(long id, ClienteSesion sesion, EstadoPedido estado, Renglon... renglones) {
        Pedido pedido = con(conId(new Pedido(1L, cuenta, sesion, "uuid-" + id, AHORA.minusSeconds(600)), id),
                "estado", estado);
        for (Renglon r : renglones) {
            new ItemPedido(1L, pedido, r.plato().getId(), (short) r.cantidad(), new BigDecimal(r.precio()), null);
        }
        return pedido;
    }

    /** El pedido 40 de CP-22-01: 2 Milanesas y 1 Flan, servidos ($ 23.000). */
    private Pedido servido40() {
        return pedido(40, sesionA, EstadoPedido.SERVIDO, x(milanesa, 2, "9500.00"), x(flan, 1, "4000.00"));
    }

    private void enLaCuenta(Pedido... enLaCuenta) {
        when(pedidos.listarDeLaCuenta(7L)).thenReturn(List.of(enLaCuenta));
    }

    private ConfirmacionDeCierreRequeridaException pideConfirmar(boolean confirmar, List<Long> lista) {
        return assertThrows(ConfirmacionDeCierreRequeridaException.class,
                () -> servicio.cerrar(CARLA, 7L, confirmar, lista));
    }

    private void nadaSeGuardoNiSePublico() {
        verify(cuentas, never()).guardar(any());
        verify(pedidos, never()).guardar(any());
        verify(tickets, never()).guardar(any());
        verifyNoInteractions(auditor, eventos);
    }

    private static List<Long> ids(List<PedidoSinServirDto> pedidos) {
        return pedidos.stream().map(PedidoSinServirDto::id).toList();
    }

    // ----- sin motivos -----

    @Test
    void CP_22_01_sin_motivos_cierra_guarda_el_total_y_arma_el_ticket() {
        enLaCuenta(servido40());

        TicketDto ticket = servicio.cerrar(CARLA, 7L, false, null);

        assertEquals(EstadoCuenta.CERRADA, cuenta.getEstado());
        assertEquals(6L, cuenta.getCerradaPorUsuarioId());
        assertEquals(AHORA, cuenta.getCerradaEn());
        assertEquals(new BigDecimal("23000.00"), cuenta.getTotal());
        verify(cuentas).guardar(cuenta);

        ArgumentCaptor<Ticket> guardado = ArgumentCaptor.forClass(Ticket.class);
        verify(tickets).guardar(guardado.capture());
        assertEquals(1L, guardado.getValue().getLocalId());
        assertEquals(cuenta, guardado.getValue().getCuenta());
        assertEquals(AHORA, guardado.getValue().getEmitidoEn());
        assertEquals(new BigDecimal("23000.00"), guardado.getValue().getTotal());

        assertEquals(99L, ticket.numero());
        assertEquals("La Esquina", ticket.local());
        assertEquals("1", ticket.mesa());
        assertEquals(AHORA, ticket.emitidoEn());
        assertEquals(new BigDecimal("23000.00"), ticket.total());
        assertEquals("X", ticket.letra());
        assertEquals("DOCUMENTO NO VÁLIDO COMO FACTURA", ticket.leyenda());
        assertEquals(List.of(
                new ItemDeTicketDto("Milanesa napolitana", 2, new BigDecimal("9500.00"), new BigDecimal("19000.00")),
                new ItemDeTicketDto("Flan casero", 1, new BigDecimal("4000.00"), new BigDecimal("4000.00"))),
                ticket.items());
    }

    @Test
    void sin_motivos_con_confirmar_true_tambien_cierra() {
        enLaCuenta(servido40());

        servicio.cerrar(CARLA, 7L, true, List.of());

        assertEquals(EstadoCuenta.CERRADA, cuenta.getEstado());
    }

    @Test
    void el_mismo_plato_al_mismo_precio_en_dos_pedidos_va_en_un_solo_renglon() {
        Pedido primero = pedido(40, sesionA, EstadoPedido.SERVIDO, x(milanesa, 1, "9500.00"), x(flan, 1, "4000.00"));
        Pedido segundo = pedido(45, sesionA, EstadoPedido.SERVIDO, x(milanesa, 1, "9500.00"));
        enLaCuenta(primero, segundo);

        TicketDto ticket = servicio.cerrar(CARLA, 7L, false, null);

        assertEquals(List.of(
                new ItemDeTicketDto("Milanesa napolitana", 2, new BigDecimal("9500.00"), new BigDecimal("19000.00")),
                new ItemDeTicketDto("Flan casero", 1, new BigDecimal("4000.00"), new BigDecimal("4000.00"))),
                ticket.items());
        assertEquals(new BigDecimal("23000.00"), ticket.total());
    }

    @Test
    void el_mismo_plato_a_distinto_precio_no_se_agrupa() {
        Pedido viejo = pedido(40, sesionA, EstadoPedido.SERVIDO, x(milanesa, 1, "9500.00"));
        Pedido nuevo = pedido(45, sesionA, EstadoPedido.SERVIDO, x(milanesa, 1, "10500.00"));
        enLaCuenta(viejo, nuevo);

        TicketDto ticket = servicio.cerrar(CARLA, 7L, false, null);

        assertEquals(List.of(
                new ItemDeTicketDto("Milanesa napolitana", 1, new BigDecimal("9500.00"), new BigDecimal("9500.00")),
                new ItemDeTicketDto("Milanesa napolitana", 1, new BigDecimal("10500.00"), new BigDecimal("10500.00"))),
                ticket.items());
        assertEquals(new BigDecimal("20000.00"), ticket.total());
    }

    @Test
    void CP_22_03_el_ticket_usa_el_precio_del_pedido_aunque_el_plato_haya_subido() {
        con(milanesa, "precio", new BigDecimal("10500.00")); // el Admin la subió después del pedido
        enLaCuenta(servido40());

        TicketDto ticket = servicio.cerrar(CARLA, 7L, false, null);

        assertEquals(new BigDecimal("9500.00"), ticket.items().get(0).precioUnitario());
        assertEquals(new BigDecimal("23000.00"), ticket.total());
        assertEquals(new BigDecimal("23000.00"), cuenta.getTotal());
    }

    @Test
    void los_nombres_de_los_platos_se_leen_en_una_sola_consulta_por_lote() {
        enLaCuenta(servido40(), pedido(45, sesionA, EstadoPedido.SERVIDO, x(milanesa, 1, "9500.00")));

        servicio.cerrar(CARLA, 7L, false, null);

        verify(carta, times(1)).buscarPorIds(eq(1L), any());
    }

    // ----- los motivos y el 409 de confirmación -----

    @Test
    void CP_22_06_cuenta_abierta_que_nadie_pidio_pide_confirmacion_y_no_toca_nada() {
        con(cuenta, "estado", EstadoCuenta.ABIERTA);
        enLaCuenta(servido40());

        ConfirmacionDeCierreRequeridaException e = pideConfirmar(false, null);

        assertEquals(List.of(MotivoDeConfirmacion.NADIE_PIDIO_LA_CUENTA), e.motivos());
        assertTrue(e.pedidosSinServir().isEmpty());
        assertEquals(MENSAJE_NADIE, e.getMessage());
        assertEquals("CONFIRMAR_CIERRE", ConfirmacionDeCierreRequeridaException.CODIGO);
        assertEquals(EstadoCuenta.ABIERTA, cuenta.getEstado());
        nadaSeGuardoNiSePublico();
    }

    @Test
    void CP_22_06_al_confirmar_la_cuenta_abierta_se_cierra_y_sale_el_ticket() {
        con(cuenta, "estado", EstadoCuenta.ABIERTA);
        enLaCuenta(servido40());

        TicketDto ticket = servicio.cerrar(CARLA, 7L, true, List.of());

        assertEquals(EstadoCuenta.CERRADA, cuenta.getEstado());
        assertEquals(new BigDecimal("23000.00"), ticket.total());
        verify(auditor).registrar(eq(1L), eq(6L), eq("CERRAR_CUENTA"), eq("CUENTA"), eq(7L),
                eq(Map.of("estado", "ABIERTA")), any());
    }

    @Test
    void CP_22_07_con_un_pedido_en_preparacion_pide_confirmacion_y_lo_lista_con_sus_platos() {
        Pedido enPreparacion = pedido(41, sesionA, EstadoPedido.EN_PREPARACION, x(milanesa, 1, "9500.00"));
        enLaCuenta(servido40(), enPreparacion);

        ConfirmacionDeCierreRequeridaException e = pideConfirmar(false, null);

        assertEquals(List.of(MotivoDeConfirmacion.PEDIDOS_SIN_SERVIR), e.motivos());
        assertEquals(MENSAJE_SIN_SERVIR, e.getMessage());
        assertEquals(List.of(new PedidoSinServirDto(41L, "EN_PREPARACION",
                List.of(new PlatoSinServirDto("Milanesa napolitana", 1)))), e.pedidosSinServir());
        assertEquals(EstadoPedido.EN_PREPARACION, enPreparacion.getEstado());
        assertEquals(EstadoCuenta.SOLICITADA, cuenta.getEstado());
        nadaSeGuardoNiSePublico();
    }

    @Test
    void los_dos_motivos_van_en_el_mismo_409_en_orden_y_con_los_dos_mensajes() {
        con(cuenta, "estado", EstadoCuenta.ABIERTA);
        enLaCuenta(servido40(), pedido(41, sesionA, EstadoPedido.PENDIENTE_APROBACION, x(flan, 2, "4000.00")));

        ConfirmacionDeCierreRequeridaException e = pideConfirmar(false, null);

        assertEquals(List.of(MotivoDeConfirmacion.NADIE_PIDIO_LA_CUENTA, MotivoDeConfirmacion.PEDIDOS_SIN_SERVIR),
                e.motivos());
        assertEquals(MENSAJE_NADIE + " " + MENSAJE_SIN_SERVIR, e.getMessage());
        assertEquals(List.of(41L), ids(e.pedidosSinServir()));
        nadaSeGuardoNiSePublico();
    }

    @Test
    void con_los_dos_motivos_una_sola_confirmacion_con_la_lista_cierra() {
        con(cuenta, "estado", EstadoCuenta.ABIERTA);
        enLaCuenta(servido40(), pedido(41, sesionA, EstadoPedido.PENDIENTE_APROBACION, x(flan, 2, "4000.00")));

        servicio.cerrar(CARLA, 7L, true, List.of(41L));

        assertEquals(EstadoCuenta.CERRADA, cuenta.getEstado());
    }

    @Test
    void los_cuatro_estados_sin_servir_aparecen_y_los_otros_no() {
        enLaCuenta(
                pedido(41, sesionA, EstadoPedido.PENDIENTE_APROBACION, x(flan, 1, "4000.00")),
                pedido(42, sesionA, EstadoPedido.APROBADO, x(flan, 1, "4000.00")),
                pedido(43, sesionA, EstadoPedido.EN_PREPARACION, x(flan, 1, "4000.00")),
                pedido(44, sesionA, EstadoPedido.LISTO, x(flan, 1, "4000.00")),
                pedido(45, sesionA, EstadoPedido.SERVIDO, x(flan, 1, "4000.00")),
                pedido(46, sesionA, EstadoPedido.RECHAZADO, x(flan, 1, "4000.00")),
                pedido(47, sesionA, EstadoPedido.CANCELADO, x(flan, 1, "4000.00")));

        ConfirmacionDeCierreRequeridaException e = pideConfirmar(false, null);

        assertEquals(List.of(41L, 42L, 43L, 44L), ids(e.pedidosSinServir()));
        assertEquals(List.of("PENDIENTE_APROBACION", "APROBADO", "EN_PREPARACION", "LISTO"),
                e.pedidosSinServir().stream().map(PedidoSinServirDto::estado).toList());
    }

    // ----- la confirmación -----

    @Test
    void CP_22_07_con_la_lista_justa_cancela_los_cuatro_estados_como_cierre_de_cuenta_y_no_suman() {
        Pedido pendiente = pedido(41, sesionA, EstadoPedido.PENDIENTE_APROBACION, x(flan, 1, "4000.00"));
        Pedido aprobado = pedido(42, sesionB, EstadoPedido.APROBADO, x(ensalada, 1, "6000.00"));
        Pedido enPreparacion = pedido(43, sesionA, EstadoPedido.EN_PREPARACION, x(milanesa, 1, "9500.00"));
        Pedido listo = pedido(44, sesionB, EstadoPedido.LISTO, x(milanesa, 1, "9500.00"));
        enLaCuenta(servido40(), pendiente, aprobado, enPreparacion, listo);

        TicketDto ticket = servicio.cerrar(CARLA, 7L, true, List.of(41L, 42L, 43L, 44L));

        for (Pedido p : List.of(pendiente, aprobado, enPreparacion, listo)) {
            assertEquals(EstadoPedido.CANCELADO, p.getEstado());
            assertEquals(6L, p.getCanceladoPorUsuarioId());
            assertEquals(AHORA, p.getCanceladoEn());
            verify(pedidos).guardar(p);
        }
        assertEquals(EstadoCuenta.CERRADA, cuenta.getEstado());
        assertEquals(new BigDecimal("23000.00"), cuenta.getTotal());
        assertEquals(new BigDecimal("23000.00"), ticket.total());
        assertEquals(2, ticket.items().size());
    }

    @Test
    void CP_22_07_un_pedido_nuevo_que_carla_no_vio_da_el_409_con_la_lista_completa_y_no_cancela_nada() {
        Pedido visto = pedido(41, sesionA, EstadoPedido.EN_PREPARACION, x(milanesa, 1, "9500.00"));
        Pedido nuevo = pedido(42, sesionA, EstadoPedido.PENDIENTE_APROBACION, x(flan, 1, "4000.00"));
        enLaCuenta(servido40(), visto, nuevo);

        ConfirmacionDeCierreRequeridaException e = pideConfirmar(true, List.of(41L));

        assertEquals(List.of(41L, 42L), ids(e.pedidosSinServir()));
        assertEquals(List.of(MotivoDeConfirmacion.PEDIDOS_SIN_SERVIR), e.motivos());
        assertEquals(EstadoPedido.EN_PREPARACION, visto.getEstado());
        assertEquals(EstadoPedido.PENDIENTE_APROBACION, nuevo.getEstado());
        assertNull(visto.getCanceladoPorUsuarioId());
        assertEquals(EstadoCuenta.SOLICITADA, cuenta.getEstado());
        nadaSeGuardoNiSePublico();
    }

    @Test
    void confirmar_true_sin_lista_o_con_lista_vacia_no_alcanza_si_hay_pedidos_sin_servir() {
        enLaCuenta(servido40(), pedido(41, sesionA, EstadoPedido.LISTO, x(milanesa, 1, "9500.00")));

        assertEquals(List.of(41L), ids(pideConfirmar(true, null).pedidosSinServir()));
        assertEquals(List.of(41L), ids(pideConfirmar(true, List.of()).pedidosSinServir()));
        nadaSeGuardoNiSePublico();
    }

    @Test
    void un_id_de_la_lista_que_ya_se_sirvio_o_se_cancelo_se_ignora_y_cierra() {
        Pedido sigueSinServir = pedido(41, sesionA, EstadoPedido.EN_PREPARACION, x(milanesa, 1, "9500.00"));
        Pedido ahoraServido = pedido(42, sesionA, EstadoPedido.SERVIDO, x(flan, 1, "4000.00"));
        Pedido loCancelo = pedido(43, sesionB, EstadoPedido.CANCELADO, x(ensalada, 1, "6000.00"));
        enLaCuenta(servido40(), sigueSinServir, ahoraServido, loCancelo);

        TicketDto ticket = servicio.cerrar(CARLA, 7L, true, List.of(41L, 42L, 43L, 999L));

        assertEquals(EstadoPedido.CANCELADO, sigueSinServir.getEstado());
        assertEquals(6L, sigueSinServir.getCanceladoPorUsuarioId());
        assertEquals(EstadoPedido.SERVIDO, ahoraServido.getEstado());
        assertNull(loCancelo.getCanceladoPorUsuarioId());
        verify(pedidos, times(1)).guardar(any());
        assertEquals(EstadoCuenta.CERRADA, cuenta.getEstado());
        assertEquals(new BigDecimal("27000.00"), ticket.total()); // el 40 y el 42, que se sirvió
    }

    @Test
    void una_lista_de_mas_con_ids_que_no_estan_sin_servir_no_frena_el_cierre() {
        enLaCuenta(servido40(), pedido(41, sesionA, EstadoPedido.APROBADO, x(flan, 1, "4000.00")));

        servicio.cerrar(CARLA, 7L, true, List.of(41L, 500L, 501L));

        assertEquals(EstadoCuenta.CERRADA, cuenta.getEstado());
    }

    // ----- el total -----

    @Test
    void CP_22_02_el_total_y_el_ticket_no_incluyen_rechazados_ni_cancelados() {
        enLaCuenta(servido40(),
                pedido(41, sesionA, EstadoPedido.RECHAZADO, x(ensalada, 1, "6000.00")),
                pedido(42, sesionB, EstadoPedido.CANCELADO, x(flan, 3, "4000.00")));

        TicketDto ticket = servicio.cerrar(CARLA, 7L, false, null);

        assertEquals(new BigDecimal("23000.00"), ticket.total());
        assertEquals(new BigDecimal("23000.00"), cuenta.getTotal());
        assertEquals(List.of("Milanesa napolitana", "Flan casero"),
                ticket.items().stream().map(ItemDeTicketDto::plato).toList());
        assertEquals(1, ticket.items().get(1).cantidad());
    }

    @Test
    void con_todo_cancelado_el_total_es_cero_y_el_ticket_sale_igual() {
        Pedido sinServir = pedido(41, sesionA, EstadoPedido.APROBADO, x(milanesa, 1, "9500.00"));
        enLaCuenta(sinServir, pedido(42, sesionA, EstadoPedido.RECHAZADO, x(flan, 1, "4000.00")));

        TicketDto ticket = servicio.cerrar(CARLA, 7L, true, List.of(41L));

        assertEquals(0, ticket.total().compareTo(BigDecimal.ZERO));
        assertEquals(0, cuenta.getTotal().compareTo(BigDecimal.ZERO));
        assertTrue(ticket.items().isEmpty());
        assertEquals(99L, ticket.numero());
        assertEquals("X", ticket.letra());
        assertEquals(EstadoCuenta.CERRADA, cuenta.getEstado());
        verify(tickets).guardar(any(Ticket.class));
    }

    @Test
    void una_cuenta_sin_pedidos_abierta_pide_confirmacion_y_confirmada_cierra_con_total_cero() {
        con(cuenta, "estado", EstadoCuenta.ABIERTA);
        enLaCuenta();

        assertEquals(List.of(MotivoDeConfirmacion.NADIE_PIDIO_LA_CUENTA), pideConfirmar(false, null).motivos());

        TicketDto ticket = servicio.cerrar(CARLA, 7L, true, null);
        assertEquals(0, ticket.total().compareTo(BigDecimal.ZERO));
    }

    // ----- auditoría -----

    @Test
    void audita_un_solo_cerrar_cuenta_con_el_total_el_ticket_y_los_pedidos_cancelados() {
        enLaCuenta(servido40(),
                pedido(41, sesionA, EstadoPedido.EN_PREPARACION, x(milanesa, 1, "9500.00")),
                pedido(42, sesionB, EstadoPedido.PENDIENTE_APROBACION, x(flan, 1, "4000.00")));

        servicio.cerrar(CARLA, 7L, true, List.of(41L, 42L));

        verify(auditor, times(1)).registrar(any(), any(), anyString(), anyString(), any(), any(), any());
        verify(auditor).registrar(1L, 6L, "CERRAR_CUENTA", "CUENTA", 7L,
                Map.of("estado", "SOLICITADA"),
                Map.of("estado", "CERRADA", "total", new BigDecimal("23000.00"), "ticketId", 99L,
                        "pedidosCancelados", List.of(41L, 42L)));
    }

    @Test
    void sin_pedidos_cancelados_la_auditoria_lleva_la_lista_vacia() {
        enLaCuenta(servido40());

        servicio.cerrar(CARLA, 7L, false, null);

        verify(auditor).registrar(1L, 6L, "CERRAR_CUENTA", "CUENTA", 7L,
                Map.of("estado", "SOLICITADA"),
                Map.of("estado", "CERRADA", "total", new BigDecimal("23000.00"), "ticketId", 99L,
                        "pedidosCancelados", List.of()));
    }

    // ----- qué se publica y a quién -----

    @Test
    void publica_la_cancelacion_de_cada_pedido_y_el_cierre_de_la_cuenta_a_quien_corresponde() {
        enLaCuenta(servido40(),
                pedido(41, sesionA, EstadoPedido.EN_PREPARACION, x(milanesa, 1, "9500.00")),
                pedido(42, sesionB, EstadoPedido.LISTO, x(flan, 1, "4000.00")));

        servicio.cerrar(CARLA, 7L, true, List.of(41L, 42L));

        ArgumentCaptor<EventoTiempoReal> aCocina = ArgumentCaptor.forClass(EventoTiempoReal.class);
        verify(eventos, times(2)).aCocina(eq(1L), aCocina.capture());
        ArgumentCaptor<EventoTiempoReal> aPantalla = ArgumentCaptor.forClass(EventoTiempoReal.class);
        verify(eventos, times(2)).aPantalla(eq(1L), aPantalla.capture());
        for (List<EventoTiempoReal> enviados : List.of(aCocina.getAllValues(), aPantalla.getAllValues())) {
            assertEquals(List.of(41L, 42L), enviados.stream().map(EventoTiempoReal::pedidoId).toList());
            for (EventoTiempoReal e : enviados) {
                assertEquals(TipoEvento.PEDIDO_CANCELADO, e.tipo());
                assertEquals(3L, e.mesaId());
                assertEquals("CANCELADO", e.estado());
            }
        }

        ArgumentCaptor<EventoTiempoReal> aCaja = ArgumentCaptor.forClass(EventoTiempoReal.class);
        verify(eventos, times(3)).aCaja(eq(1L), aCaja.capture());
        List<EventoTiempoReal> deCaja = aCaja.getAllValues();
        assertEquals(List.of(TipoEvento.PEDIDO_CANCELADO, TipoEvento.PEDIDO_CANCELADO, TipoEvento.CUENTA_CERRADA),
                deCaja.stream().map(EventoTiempoReal::tipo).toList());
        assertEquals(List.of(41L, 42L), deCaja.subList(0, 2).stream().map(EventoTiempoReal::pedidoId).toList());

        ArgumentCaptor<EventoTiempoReal> aMozos = ArgumentCaptor.forClass(EventoTiempoReal.class);
        verify(eventos, times(1)).aMozos(eq(1L), aMozos.capture());
        for (EventoTiempoReal cerrada : List.of(deCaja.get(2), aMozos.getValue())) {
            assertEquals(TipoEvento.CUENTA_CERRADA, cerrada.tipo());
            assertNull(cerrada.pedidoId());
            assertEquals(3L, cerrada.mesaId());
            assertEquals(7L, cerrada.cuentaId());
            assertEquals("CERRADA", cerrada.estado());
        }

        ArgumentCaptor<EventoTiempoReal> aClienteA = ArgumentCaptor.forClass(EventoTiempoReal.class);
        verify(eventos).aCliente(eq("token-de-A"), aClienteA.capture());
        assertEquals(TipoEvento.CANCELADO, aClienteA.getValue().tipo());
        assertEquals(41L, aClienteA.getValue().pedidoId());
        ArgumentCaptor<EventoTiempoReal> aClienteB = ArgumentCaptor.forClass(EventoTiempoReal.class);
        verify(eventos).aCliente(eq("token-de-B"), aClienteB.capture());
        assertEquals(TipoEvento.CANCELADO, aClienteB.getValue().tipo());
        assertEquals(42L, aClienteB.getValue().pedidoId());
        verify(eventos, times(2)).aCliente(anyString(), any());
        verify(eventos, never()).aAlertas(any(), any());
    }

    @Test
    void sin_pedidos_cancelados_solo_se_publica_el_cierre_a_caja_y_a_mozos() {
        enLaCuenta(servido40());

        servicio.cerrar(CARLA, 7L, false, null);

        verify(eventos).aCaja(eq(1L), any(EventoTiempoReal.class));
        verify(eventos).aMozos(eq(1L), any(EventoTiempoReal.class));
        verify(eventos, never()).aCocina(any(), any());
        verify(eventos, never()).aPantalla(any(), any());
        verify(eventos, never()).aCliente(any(), any());
    }

    @Test
    void primero_se_guarda_todo_y_despues_se_audita_y_se_publica() {
        enLaCuenta(servido40(), pedido(41, sesionA, EstadoPedido.APROBADO, x(flan, 1, "4000.00")));

        servicio.cerrar(CARLA, 7L, true, List.of(41L));

        InOrder orden = inOrder(cuentas, pedidos, tickets, auditor, eventos);
        orden.verify(cuentas).guardar(cuenta);
        orden.verify(pedidos).guardar(any(Pedido.class));
        orden.verify(tickets).guardar(any(Ticket.class));
        orden.verify(auditor).registrar(any(), any(), anyString(), anyString(), any(), any(), any());
        orden.verify(eventos).aCaja(eq(1L), any(EventoTiempoReal.class));
    }

    // ----- errores -----

    @Test
    void una_cuenta_de_otro_local_o_que_no_existe_da_404_sin_tocar_nada() {
        UsuarioAutenticado carlaDeOtroLocal = new UsuarioAutenticado(8L, "CAJA", 2L, false, AHORA.plusSeconds(3600));
        when(cuentas.buscarPorId(7L, 2L)).thenReturn(Optional.empty());
        when(cuentas.buscarPorId(777L, 1L)).thenReturn(Optional.empty());

        NoEncontradoException deOtroLocal = assertThrows(NoEncontradoException.class,
                () -> servicio.cerrar(carlaDeOtroLocal, 7L, true, List.of()));
        NoEncontradoException inexistente = assertThrows(NoEncontradoException.class,
                () -> servicio.cerrar(CARLA, 777L, true, List.of()));

        assertEquals("La cuenta no existe", deOtroLocal.getMessage());
        assertEquals("La cuenta no existe", inexistente.getMessage());
        verifyNoInteractions(pedidos, tickets, auditor, eventos);
        assertEquals(EstadoCuenta.SOLICITADA, cuenta.getEstado());
    }

    @Test
    void el_gerente_dueno_no_tiene_local_y_no_cierra_cuentas() {
        UsuarioAutenticado gerente = new UsuarioAutenticado(1L, "GERENTE_DUENO", null, false, AHORA.plusSeconds(600));

        assertThrows(AccesoDenegadoException.class, () -> servicio.cerrar(gerente, 7L, true, List.of()));

        verifyNoInteractions(cuentas, pedidos, tickets, auditor, eventos);
    }

    @Test
    void una_cuenta_ya_cerrada_da_409_sin_ticket_ni_auditoria_ni_eventos() {
        con(cuenta, "estado", EstadoCuenta.CERRADA);
        enLaCuenta(servido40());

        ReglaDeNegocioException e = assertThrows(ReglaDeNegocioException.class,
                () -> servicio.cerrar(CARLA, 7L, true, List.of()));

        assertEquals("La cuenta ya está cerrada", e.getMessage());
        nadaSeGuardoNiSePublico();
    }

    @Test
    void un_choque_de_version_al_guardar_un_pedido_da_409_sin_ticket_ni_auditoria_ni_eventos() {
        enLaCuenta(servido40(), pedido(41, sesionA, EstadoPedido.LISTO, x(milanesa, 1, "9500.00")));
        when(pedidos.guardar(any(Pedido.class))).thenThrow(new ConflictoDeConcurrenciaException("Cocina lo cambió"));

        assertThrows(ConflictoDeConcurrenciaException.class, () -> servicio.cerrar(CARLA, 7L, true, List.of(41L)));

        verify(tickets, never()).guardar(any());
        verifyNoInteractions(auditor, eventos);
    }

    @Test
    void un_choque_de_version_al_guardar_la_cuenta_da_409_sin_ticket_ni_auditoria_ni_eventos() {
        enLaCuenta(servido40());
        when(cuentas.guardar(any(Cuenta.class))).thenThrow(new ConflictoDeConcurrenciaException("otro la cambió"));

        assertThrows(ConflictoDeConcurrenciaException.class, () -> servicio.cerrar(CARLA, 7L, false, null));

        verify(tickets, never()).guardar(any());
        verifyNoInteractions(auditor, eventos);
    }
}
