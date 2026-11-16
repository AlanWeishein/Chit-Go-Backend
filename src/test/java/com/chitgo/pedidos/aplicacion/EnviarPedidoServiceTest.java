package com.chitgo.pedidos.aplicacion;

import static com.chitgo.pruebas.Entidades.con;
import static com.chitgo.pruebas.Entidades.conId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;

import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.ConflictoDeConcurrenciaException;
import com.chitgo.compartido.dominio.RelojDePrueba;
import com.chitgo.compartido.dominio.ReglaDeNegocioException;
import com.chitgo.menu.dominio.CategoriaMenu;
import com.chitgo.menu.dominio.Plato;
import com.chitgo.menu.dominio.puertos.CartaRepository;
import com.chitgo.pedidos.aplicacion.dto.ItemSolicitado;
import com.chitgo.pedidos.aplicacion.dto.ResultadoDeEnvio;
import com.chitgo.pedidos.dominio.ClienteSesion;
import com.chitgo.pedidos.dominio.Cuenta;
import com.chitgo.pedidos.dominio.EstadoCuenta;
import com.chitgo.pedidos.dominio.EstadoPedido;
import com.chitgo.pedidos.dominio.EventoTiempoReal;
import com.chitgo.pedidos.dominio.ItemPedido;
import com.chitgo.pedidos.dominio.Mesa;
import com.chitgo.pedidos.dominio.Pedido;
import com.chitgo.pedidos.dominio.PedidoDuplicadoException;
import com.chitgo.pedidos.dominio.puertos.ClienteSesionRepository;
import com.chitgo.pedidos.dominio.puertos.CuentaRepository;
import com.chitgo.pedidos.dominio.puertos.PedidoRepository;
import com.chitgo.pedidos.dominio.puertos.PublicadorDeEventos;

class EnviarPedidoServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-20T20:00:00Z");
    private static final String TOKEN = "token-de-A";

    private final ClienteSesionRepository sesiones = mock(ClienteSesionRepository.class);
    private final CuentaRepository cuentas = mock(CuentaRepository.class);
    private final PedidoRepository pedidos = mock(PedidoRepository.class);
    private final CartaRepository carta = mock(CartaRepository.class);
    private final PublicadorDeEventos eventos = mock(PublicadorDeEventos.class);
    private final Mesa mesa = conId(new Mesa(1L, "1", "qr-1"), 3L);
    private final Cuenta cuenta = conId(new Cuenta(1L, mesa, 5L, AHORA), 7L);
    private final ClienteSesion sesion = conId(new ClienteSesion(1L, cuenta, TOKEN, true), 10L);
    private final Plato milanesa = plato(1, "Milanesa napolitana", "9500.00", true);
    private final Plato flan = plato(2, "Flan casero", "4000.00", true);
    private EnviarPedidoService servicio;

    private static Plato plato(long id, String nombre, String precio, boolean activo) {
        CategoriaMenu categoria = conId(new CategoriaMenu(1L, "Principales", (short) 1), 1L);
        Plato plato = conId(new Plato(1L, categoria, nombre, null, new BigDecimal(precio), null), id);
        return con(plato, "activo", activo);
    }

    @BeforeEach
    void preparar() {
        servicio = new EnviarPedidoService(sesiones, cuentas, pedidos, carta, eventos, new ArmadorDePedidos(carta),
                new RelojDePrueba(AHORA), mock(PlatformTransactionManager.class));
        when(sesiones.buscarPorToken(TOKEN)).thenReturn(Optional.of(sesion));
        when(pedidos.buscarPorUuidCliente(any())).thenReturn(Optional.empty());
        when(pedidos.guardar(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
        when(carta.buscarPorIds(eq(1L), any())).thenReturn(List.of(milanesa, flan));
    }

    private static List<ItemSolicitado> milanesasYFlan() {
        return List.of(new ItemSolicitado(1L, 2, "sin huevo"), new ItemSolicitado(2L, 1, null));
    }

    @Test
    void CP_03_01_y_03_08_el_pedido_nace_pendiente_y_con_el_precio_del_servidor() {
        ResultadoDeEnvio resultado = servicio.enviar(TOKEN, UUID.randomUUID(), milanesasYFlan());

        assertTrue(resultado.creado());
        assertEquals("PENDIENTE_APROBACION", resultado.pedido().estado());
        assertEquals(new BigDecimal("23000.00"), resultado.pedido().total());

        ArgumentCaptor<Pedido> guardado = ArgumentCaptor.forClass(Pedido.class);
        verify(pedidos).guardar(guardado.capture());
        assertEquals(EstadoPedido.PENDIENTE_APROBACION, guardado.getValue().getEstado());
        List<ItemPedido> items = guardado.getValue().getItems();
        assertEquals(new BigDecimal("9500.00"), items.get(0).getPrecioUnitario());
        assertEquals("sin huevo", items.get(0).getObservacion());
        assertEquals(new BigDecimal("4000.00"), items.get(1).getPrecioUnitario());
        verify(eventos).aCaja(eq(1L), any(EventoTiempoReal.class));
    }

    @Test
    void CP_01_03_una_sesion_de_lectura_no_puede_pedir() {
        con(sesion, "esIniciador", false);
        assertThrows(AccesoDenegadoException.class, () -> servicio.enviar(TOKEN, UUID.randomUUID(), milanesasYFlan()));
        verify(pedidos, never()).guardar(any());
    }

    @Test
    void sin_token_de_sesion_es_403() {
        assertThrows(AccesoDenegadoException.class, () -> servicio.enviar(null, UUID.randomUUID(), milanesasYFlan()));
    }

    @Test
    void CP_03_04_reenviar_el_mismo_pedido_devuelve_el_que_ya_existe() {
        UUID uuid = UUID.randomUUID();
        Pedido existente = conId(new Pedido(1L, cuenta, sesion, uuid.toString(), AHORA), 5L);
        when(pedidos.buscarPorUuidCliente(uuid.toString())).thenReturn(Optional.of(existente));

        ResultadoDeEnvio resultado = servicio.enviar(TOKEN, uuid, milanesasYFlan());

        assertFalse(resultado.creado());
        assertEquals(5L, resultado.pedido().id());
        verify(pedidos, never()).guardar(any());
    }

    @Test
    void el_pedido_de_otra_sesion_con_el_mismo_uuid_es_403() {
        UUID uuid = UUID.randomUUID();
        ClienteSesion otra = conId(new ClienteSesion(1L, cuenta, "token-de-B", true), 11L);
        when(pedidos.buscarPorUuidCliente(uuid.toString()))
                .thenReturn(Optional.of(conId(new Pedido(1L, cuenta, otra, uuid.toString(), AHORA), 5L)));
        assertThrows(AccesoDenegadoException.class, () -> servicio.enviar(TOKEN, uuid, milanesasYFlan()));
    }

    @Test
    void dos_envios_iguales_a_la_vez_el_segundo_recibe_el_pedido_que_gano() {
        UUID uuid = UUID.randomUUID();
        Pedido ganador = conId(new Pedido(1L, cuenta, sesion, uuid.toString(), AHORA), 5L);
        AtomicInteger busquedas = new AtomicInteger();
        when(pedidos.buscarPorUuidCliente(uuid.toString()))
                .thenAnswer(inv -> busquedas.getAndIncrement() == 0 ? Optional.empty() : Optional.of(ganador));
        when(pedidos.guardar(any(Pedido.class))).thenAnswer(inv -> {
            throw new PedidoDuplicadoException();
        });

        ResultadoDeEnvio resultado = servicio.enviar(TOKEN, uuid, milanesasYFlan());

        assertFalse(resultado.creado());
        assertEquals(5L, resultado.pedido().id());
        verify(eventos, never()).aCaja(any(), any());
    }

    @Test
    void un_plato_que_dejo_de_estar_disponible_no_se_puede_pedir() {
        con(milanesa, "activo", false);
        ReglaDeNegocioException e = assertThrows(ReglaDeNegocioException.class,
                () -> servicio.enviar(TOKEN, UUID.randomUUID(), List.of(new ItemSolicitado(1L, 1, null))));
        assertTrue(e.getMessage().contains("ya no está disponible"));
        verify(pedidos, never()).guardar(any());
    }

    @Test
    void con_la_cuenta_cerrada_no_se_puede_pedir() {
        con(cuenta, "estado", EstadoCuenta.CERRADA);
        assertThrows(ReglaDeNegocioException.class,
                () -> servicio.enviar(TOKEN, UUID.randomUUID(), milanesasYFlan()));
    }

    @Test
    void CP_08_04_con_la_cuenta_solicitada_se_registra_el_pedido_la_cuenta_vuelve_a_abierta_y_se_avisa_a_caja() {
        con(cuenta, "estado", EstadoCuenta.SOLICITADA);

        servicio.enviar(TOKEN, UUID.randomUUID(), milanesasYFlan());

        assertEquals(EstadoCuenta.ABIERTA, cuenta.getEstado());
        verify(cuentas).guardar(cuenta);
        verify(cuentas, never()).subirVersion(any());
        verify(eventos, times(2)).aCaja(eq(1L), any(EventoTiempoReal.class)); // cuenta reabierta + pedido enviado
    }

    @Test
    void un_pedido_sin_platos_es_400() {
        assertThrows(com.chitgo.compartido.dominio.SolicitudInvalidaException.class,
                () -> servicio.enviar(TOKEN, UUID.randomUUID(), List.of()));
    }

    @Test
    void la_cantidad_y_la_observacion_tienen_limites() {
        assertThrows(com.chitgo.compartido.dominio.SolicitudInvalidaException.class,
                () -> new ItemSolicitado(1L, 0, null));
        assertThrows(com.chitgo.compartido.dominio.SolicitudInvalidaException.class,
                () -> new ItemSolicitado(1L, 1, "x".repeat(201)));
    }

    // ----- el pedido que entra mientras se cierra la cuenta (opción A, 30/10) -----

    @Test
    void con_la_cuenta_abierta_se_sube_la_version_antes_de_guardar_el_pedido_y_no_se_guarda_la_cuenta() {
        servicio.enviar(TOKEN, UUID.randomUUID(), milanesasYFlan());

        InOrder orden = inOrder(cuentas, pedidos);
        orden.verify(cuentas).subirVersion(cuenta);
        orden.verify(pedidos).guardar(any(Pedido.class));
        verify(cuentas, never()).guardar(any());
        assertEquals(EstadoCuenta.ABIERTA, cuenta.getEstado());
        verify(eventos, times(1)).aCaja(eq(1L), any(EventoTiempoReal.class)); // solo PEDIDO_ENVIADO
    }

    @Test
    void con_la_cuenta_solicitada_se_guarda_antes_del_pedido_y_no_se_sube_la_version_aparte() {
        con(cuenta, "estado", EstadoCuenta.SOLICITADA);

        servicio.enviar(TOKEN, UUID.randomUUID(), milanesasYFlan());

        InOrder orden = inOrder(cuentas, pedidos);
        orden.verify(cuentas).guardar(cuenta);
        orden.verify(pedidos).guardar(any(Pedido.class));
        verify(cuentas, never()).subirVersion(any());
    }

    @Test
    void un_reintento_con_el_mismo_uuid_no_toca_la_cuenta() {
        UUID uuid = UUID.randomUUID();
        Pedido existente = conId(new Pedido(1L, cuenta, sesion, uuid.toString(), AHORA), 5L);
        when(pedidos.buscarPorUuidCliente(uuid.toString())).thenReturn(Optional.of(existente));

        servicio.enviar(TOKEN, uuid, milanesasYFlan());

        verifyNoInteractions(cuentas);
    }

    @Test
    void con_la_cuenta_cerrada_no_se_toca_la_version_ni_se_guarda_nada() {
        con(cuenta, "estado", EstadoCuenta.CERRADA);

        assertThrows(ReglaDeNegocioException.class, () -> servicio.enviar(TOKEN, UUID.randomUUID(), milanesasYFlan()));

        verifyNoInteractions(cuentas);
        verify(pedidos, never()).guardar(any());
    }

    @Test
    void si_el_cierre_gano_el_choque_de_la_cuenta_sin_pedido_con_ese_uuid_es_409_y_no_se_guarda_ni_avisa_nada() {
        doThrow(new ConflictoDeConcurrenciaException("La cuenta cambió justo antes")).when(cuentas).subirVersion(cuenta);

        ConflictoDeConcurrenciaException e = assertThrows(ConflictoDeConcurrenciaException.class,
                () -> servicio.enviar(TOKEN, UUID.randomUUID(), milanesasYFlan()));

        assertEquals("La cuenta cambió justo antes", e.getMessage());
        verify(pedidos, never()).guardar(any());
        verify(eventos, never()).aCaja(any(), any());
    }

    @Test
    void si_el_choque_fue_de_un_envio_igual_a_la_vez_el_segundo_recibe_el_pedido_que_gano_con_200() {
        UUID uuid = UUID.randomUUID();
        Pedido ganador = conId(new Pedido(1L, cuenta, sesion, uuid.toString(), AHORA), 5L);
        AtomicInteger busquedas = new AtomicInteger();
        when(pedidos.buscarPorUuidCliente(uuid.toString()))
                .thenAnswer(inv -> busquedas.getAndIncrement() == 0 ? Optional.empty() : Optional.of(ganador));
        doThrow(new ConflictoDeConcurrenciaException("La cuenta cambió justo antes")).when(cuentas).subirVersion(cuenta);

        ResultadoDeEnvio resultado = servicio.enviar(TOKEN, uuid, milanesasYFlan());

        assertFalse(resultado.creado());
        assertEquals(5L, resultado.pedido().id());
        verify(eventos, never()).aCaja(any(), any());
    }

    @Test
    void si_el_pedido_que_gano_es_de_otra_sesion_el_choque_termina_en_403() {
        UUID uuid = UUID.randomUUID();
        ClienteSesion otra = conId(new ClienteSesion(1L, cuenta, "token-de-B", true), 11L);
        Pedido ganador = conId(new Pedido(1L, cuenta, otra, uuid.toString(), AHORA), 5L);
        AtomicInteger busquedas = new AtomicInteger();
        when(pedidos.buscarPorUuidCliente(uuid.toString()))
                .thenAnswer(inv -> busquedas.getAndIncrement() == 0 ? Optional.empty() : Optional.of(ganador));
        doThrow(new ConflictoDeConcurrenciaException("La cuenta cambió justo antes")).when(cuentas).subirVersion(cuenta);

        assertThrows(AccesoDenegadoException.class, () -> servicio.enviar(TOKEN, uuid, milanesasYFlan()));
    }
}
