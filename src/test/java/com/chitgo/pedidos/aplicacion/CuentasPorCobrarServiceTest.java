package com.chitgo.pedidos.aplicacion;

import static com.chitgo.pruebas.Entidades.con;
import static com.chitgo.pruebas.Entidades.conId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.SolicitudInvalidaException;
import com.chitgo.pedidos.aplicacion.dto.CuentaPorCobrarDto;
import com.chitgo.pedidos.dominio.ClienteSesion;
import com.chitgo.pedidos.dominio.Cuenta;
import com.chitgo.pedidos.dominio.EstadoCuenta;
import com.chitgo.pedidos.dominio.EstadoPedido;
import com.chitgo.pedidos.dominio.ItemPedido;
import com.chitgo.pedidos.dominio.Mesa;
import com.chitgo.pedidos.dominio.Pedido;
import com.chitgo.pedidos.dominio.puertos.CuentaRepository;
import com.chitgo.pedidos.dominio.puertos.PedidoRepository;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

class CuentasPorCobrarServiceTest {

    private static final Instant LAS_20 = Instant.parse("2026-10-30T20:00:00Z");
    private static final String LOS_DOS = "ABIERTA,SOLICITADA";
    private static final UsuarioAutenticado CARLA = new UsuarioAutenticado(6L, "CAJA", 1L, false, LAS_20.plusSeconds(3600));

    private final CuentaRepository cuentas = mock(CuentaRepository.class);
    private final PedidoRepository pedidos = mock(PedidoRepository.class);
    private final CuentasPorCobrarService servicio = new CuentasPorCobrarService(cuentas, pedidos);

    private final List<Cuenta> delLocal = new ArrayList<>();
    private final List<Pedido> deLasCuentas = new ArrayList<>();

    @BeforeEach
    void preparar() {
        when(cuentas.listarDelLocal(eq(1L), any())).thenReturn(delLocal);
        when(pedidos.listarDeLasCuentas(any())).thenReturn(deLasCuentas);
    }

    private static Instant a(String hora) {
        return Instant.parse("2026-10-30T" + hora + "Z");
    }

    private Cuenta abierta(long id, String mesa, String abiertaEn) {
        Mesa m = conId(new Mesa(1L, mesa, "qr-" + mesa), id + 100);
        Cuenta cuenta = conId(new Cuenta(1L, m, 5L, a(abiertaEn)), id);
        delLocal.add(cuenta);
        return cuenta;
    }

    private Cuenta solicitada(long id, String mesa, String abiertaEn, String solicitadaEn) {
        Cuenta cuenta = abierta(id, mesa, abiertaEn);
        con(cuenta, "estado", EstadoCuenta.SOLICITADA);
        return con(cuenta, "solicitadaEn", a(solicitadaEn));
    }

    /** Un pedido de la cuenta con un solo ítem: cantidad por precio. */
    private Pedido pedido(long id, Cuenta cuenta, EstadoPedido estado, int cantidad, String precio) {
        ClienteSesion sesion = conId(new ClienteSesion(1L, cuenta, "token-" + id, true), id + 500);
        Pedido pedido = con(conId(new Pedido(1L, cuenta, sesion, "uuid-" + id, LAS_20), id), "estado", estado);
        new ItemPedido(1L, pedido, 1L, (short) cantidad, new BigDecimal(precio), null);
        deLasCuentas.add(pedido);
        return pedido;
    }

    private List<Long> ids(List<CuentaPorCobrarDto> lista) {
        return lista.stream().map(CuentaPorCobrarDto::id).toList();
    }

    @Test
    void primero_las_solicitadas_por_cuando_se_pidieron_y_despues_las_abiertas_por_cuando_se_abrieron() {
        abierta(1, "1", "19:00:00");
        solicitada(2, "2", "18:00:00", "20:10:00");
        abierta(3, "3", "18:30:00");
        solicitada(4, "4", "19:30:00", "20:05:00");
        abierta(6, "6", "19:00:00");
        abierta(5, "5", "19:00:00");

        List<CuentaPorCobrarDto> lista = servicio.obtener(CARLA, LOS_DOS);

        // Las que se abrieron juntas salen por id.
        assertEquals(List.of(4L, 2L, 3L, 1L, 5L, 6L), ids(lista));
    }

    @Test
    void cada_cuenta_trae_la_mesa_el_estado_y_las_horas() {
        solicitada(7, "12", "19:00:00", "20:05:00");
        abierta(8, "3", "19:30:00");

        List<CuentaPorCobrarDto> lista = servicio.obtener(CARLA, LOS_DOS);

        assertEquals(new CuentaPorCobrarDto(7L, "12", "SOLICITADA", a("19:00:00"), a("20:05:00"),
                new BigDecimal("0.00"), 0), lista.get(0));
        CuentaPorCobrarDto abierta = lista.get(1);
        assertEquals("3", abierta.mesa());
        assertEquals("ABIERTA", abierta.estado());
        assertNull(abierta.solicitadaEn());
    }

    @Test
    void el_total_parcial_no_suma_rechazados_ni_cancelados_y_cuenta_los_sin_servir() {
        Cuenta cuenta = solicitada(7, "1", "19:00:00", "20:05:00");
        pedido(40, cuenta, EstadoPedido.SERVIDO, 2, "9500.00");
        pedido(41, cuenta, EstadoPedido.PENDIENTE_APROBACION, 1, "4000.00");
        pedido(42, cuenta, EstadoPedido.APROBADO, 1, "6000.00");
        pedido(43, cuenta, EstadoPedido.EN_PREPARACION, 1, "4000.00");
        pedido(44, cuenta, EstadoPedido.LISTO, 1, "6000.00");
        pedido(45, cuenta, EstadoPedido.RECHAZADO, 3, "9500.00");
        pedido(46, cuenta, EstadoPedido.CANCELADO, 1, "6000.00");

        CuentaPorCobrarDto dto = servicio.obtener(CARLA, LOS_DOS).get(0);

        // Lo sin servir suma: si se cobrara ahora, el cierre lo cancelaría, pero hasta entonces es lo que va.
        assertEquals(new BigDecimal("39000.00"), dto.total());
        assertEquals(4, dto.pedidosSinServir());
    }

    @Test
    void el_total_usa_el_precio_de_cada_pedido() {
        Cuenta cuenta = abierta(7, "1", "19:00:00");
        pedido(40, cuenta, EstadoPedido.SERVIDO, 1, "9500.00");
        pedido(41, cuenta, EstadoPedido.SERVIDO, 1, "10500.00");

        assertEquals(new BigDecimal("20000.00"), servicio.obtener(CARLA, LOS_DOS).get(0).total());
    }

    @Test
    void los_pedidos_van_a_su_cuenta_y_una_cuenta_sin_pedidos_tiene_total_0() {
        Cuenta conPedidos = solicitada(7, "1", "19:00:00", "20:05:00");
        abierta(8, "2", "19:30:00");
        Cuenta otra = abierta(9, "3", "19:40:00");
        pedido(40, conPedidos, EstadoPedido.SERVIDO, 2, "9500.00");
        pedido(41, otra, EstadoPedido.LISTO, 1, "4000.00");

        List<CuentaPorCobrarDto> lista = servicio.obtener(CARLA, LOS_DOS);

        assertEquals(new BigDecimal("19000.00"), lista.get(0).total());
        assertEquals(0, lista.get(0).pedidosSinServir());
        assertEquals(new BigDecimal("0.00"), lista.get(1).total());
        assertEquals(0, lista.get(1).pedidosSinServir());
        assertEquals(new BigDecimal("4000.00"), lista.get(2).total());
        assertEquals(1, lista.get(2).pedidosSinServir());
    }

    @Test
    void son_dos_consultas_cuantas_sean_las_cuentas_las_cuentas_del_local_y_los_pedidos_por_lote() {
        abierta(1, "1", "19:00:00");
        abierta(2, "2", "19:10:00");
        abierta(3, "3", "19:20:00");

        servicio.obtener(CARLA, LOS_DOS);

        verify(cuentas, times(1)).listarDelLocal(eq(1L), any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> lote = ArgumentCaptor.forClass(Collection.class);
        verify(pedidos, times(1)).listarDeLasCuentas(lote.capture());
        assertEquals(Set.of(1L, 2L, 3L), Set.copyOf(lote.getValue()));
        verify(pedidos, never()).listarDeLaCuenta(anyLong());
    }

    @Test
    void sin_cuentas_la_lista_sale_vacia_y_no_se_buscan_pedidos() {
        assertTrue(servicio.obtener(CARLA, LOS_DOS).isEmpty());
        verify(pedidos, never()).listarDeLasCuentas(any());
    }

    @Test
    void sin_estado_el_controlador_manda_los_dos_y_se_piden_los_dos() {
        servicio.obtener(CARLA, LOS_DOS);

        verify(cuentas).listarDelLocal(1L, EnumSet.of(EstadoCuenta.ABIERTA, EstadoCuenta.SOLICITADA));
    }

    @Test
    void con_un_solo_estado_se_pide_solo_ese() {
        servicio.obtener(CARLA, "SOLICITADA");

        verify(cuentas).listarDelLocal(1L, EnumSet.of(EstadoCuenta.SOLICITADA));
    }

    @ParameterizedTest
    @ValueSource(strings = { "SOLICITADA,ABIERTA", "ABIERTA, SOLICITADA", "ABIERTA,ABIERTA,SOLICITADA" })
    void el_orden_los_espacios_y_los_repetidos_no_cambian_lo_que_se_pide(String estado) {
        servicio.obtener(CARLA, estado);

        verify(cuentas).listarDelLocal(1L, EnumSet.of(EstadoCuenta.ABIERTA, EstadoCuenta.SOLICITADA));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "CERRADA", "ABIERTA,CERRADA", "", "ABIERTA,", ",", "abierta", "PENDIENTE_APROBACION",
            "CUALQUIERA" })
    void cerrada_o_cualquier_otro_valor_da_400_sin_consultar_nada(String estado) {
        SolicitudInvalidaException e = assertThrows(SolicitudInvalidaException.class,
                () -> servicio.obtener(CARLA, estado));

        assertEquals("Las cuentas por cobrar se piden con estado ABIERTA, SOLICITADA o los dos separados por coma",
                e.getMessage());
        verifyNoInteractions(cuentas, pedidos);
    }

    @Test
    void el_gerente_dueno_no_tiene_local_y_no_opera_la_caja() {
        UsuarioAutenticado gerente = new UsuarioAutenticado(1L, "GERENTE_DUENO", null, false, LAS_20.plusSeconds(600));

        assertThrows(AccesoDenegadoException.class, () -> servicio.obtener(gerente, LOS_DOS));
        verifyNoInteractions(cuentas, pedidos);
    }

    @Test
    void se_piden_las_cuentas_del_local_de_la_sesion() {
        UsuarioAutenticado deElPuerto = new UsuarioAutenticado(9L, "CAJA", 2L, false, LAS_20.plusSeconds(600));

        servicio.obtener(deElPuerto, LOS_DOS);

        verify(cuentas).listarDelLocal(eq(2L), any());
    }
}
