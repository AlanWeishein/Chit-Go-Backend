package com.chitgo.pedidos.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.RelojDePrueba;
import com.chitgo.compartido.dominio.SolicitudInvalidaException;
import com.chitgo.pedidos.aplicacion.dto.ItemPorAprobarDto;
import com.chitgo.pedidos.aplicacion.dto.PedidoPorAprobarDto;
import com.chitgo.pedidos.dominio.puertos.PedidosPorAprobar;
import com.chitgo.pedidos.dominio.puertos.PedidosPorAprobar.ItemPorAprobar;
import com.chitgo.pedidos.dominio.puertos.PedidosPorAprobar.PedidoPorAprobar;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

class ColaDeAprobacionServiceTest {

    private static final Instant LAS_20 = Instant.parse("2026-10-20T20:00:00Z");
    private static final String PENDIENTE = "PENDIENTE_APROBACION";
    private static final UsuarioAutenticado CARLA = new UsuarioAutenticado(6L, "CAJA", 1L, false, LAS_20.plusSeconds(3600));

    private final RelojDePrueba reloj = new RelojDePrueba(LAS_20);
    private final List<PedidoPorAprobar> pendientes = new ArrayList<>();
    private final List<Long> localesPedidos = new ArrayList<>();

    /** Puerto falso: devuelve los pendientes cargados en el test y anota qué local le pidieron. */
    private final PedidosPorAprobar puerto = localId -> {
        localesPedidos.add(localId);
        return pendientes;
    };
    private final ColaDeAprobacionService servicio = new ColaDeAprobacionService(puerto, reloj);

    private static PedidoPorAprobar pedido(long id, String hora, ItemPorAprobar... items) {
        return new PedidoPorAprobar(id, "1", Instant.parse("2026-10-20T" + hora + "Z"), List.of(items));
    }

    private static ItemPorAprobar milanesa() {
        return new ItemPorAprobar(1L, "Milanesa napolitana", 2, "sin cebolla", true);
    }

    @Test
    void CP_19_02_salen_del_mas_viejo_al_mas_nuevo_y_si_llegaron_juntos_por_id() {
        pendientes.add(pedido(30, "20:02:00", milanesa()));
        pendientes.add(pedido(12, "20:00:00", milanesa()));
        pendientes.add(pedido(11, "20:01:00", milanesa()));
        pendientes.add(pedido(10, "20:01:00", milanesa()));
        reloj.avanzar(Duration.ofMinutes(5));

        List<PedidoPorAprobarDto> cola = servicio.obtener(CARLA, PENDIENTE);

        assertEquals(List.of(12L, 10L, 11L, 30L), cola.stream().map(PedidoPorAprobarDto::id).toList());
        assertEquals(List.of(1L), localesPedidos);
    }

    @Test
    void CP_20_01_a_los_3_minutos_justos_todavia_no_esta_demorado() {
        pendientes.add(pedido(1, "20:00:00", milanesa()));
        reloj.avanzar(Duration.ofMinutes(3));

        PedidoPorAprobarDto p = servicio.obtener(CARLA, PENDIENTE).get(0);

        assertEquals(180, p.segundosDeEspera());
        assertFalse(p.demorado());
    }

    @Test
    void CP_20_01_a_los_3_minutos_y_un_segundo_esta_demorado() {
        pendientes.add(pedido(1, "20:00:00", milanesa()));
        reloj.avanzar(Duration.ofSeconds(181));

        PedidoPorAprobarDto p = servicio.obtener(CARLA, PENDIENTE).get(0);

        assertEquals(181, p.segundosDeEspera());
        assertTrue(p.demorado());
    }

    @Test
    void cada_item_trae_el_plato_la_cantidad_la_observacion_y_si_esta_disponible() {
        pendientes.add(pedido(1, "20:00:00", milanesa(),
                new ItemPorAprobar(2L, "Ensalada mixta", 1, null, false)));

        PedidoPorAprobarDto p = servicio.obtener(CARLA, PENDIENTE).get(0);

        assertEquals("1", p.mesa());
        assertEquals(LAS_20, p.enviadoEn());
        assertEquals(List.of(
                new ItemPorAprobarDto(1L, "Milanesa napolitana", 2, "sin cebolla", true),
                new ItemPorAprobarDto(2L, "Ensalada mixta", 1, null, false)), p.items());
    }

    @Test
    void sin_pendientes_la_cola_sale_vacia() {
        assertTrue(servicio.obtener(CARLA, PENDIENTE).isEmpty());
    }

    @Test
    void el_gerente_dueno_no_tiene_local_y_no_opera_la_caja() {
        UsuarioAutenticado gerente = new UsuarioAutenticado(1L, "GERENTE_DUENO", null, false, LAS_20.plusSeconds(600));

        assertThrows(AccesoDenegadoException.class, () -> servicio.obtener(gerente, PENDIENTE));
        assertTrue(localesPedidos.isEmpty());
    }

    @Test
    void otro_estado_da_400() {
        assertThrows(SolicitudInvalidaException.class, () -> servicio.obtener(CARLA, "APROBADO"));
        assertThrows(SolicitudInvalidaException.class, () -> servicio.obtener(CARLA, "CUALQUIERA"));
        assertTrue(localesPedidos.isEmpty());
    }
}
