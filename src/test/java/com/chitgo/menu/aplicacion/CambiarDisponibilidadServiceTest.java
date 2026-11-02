package com.chitgo.menu.aplicacion;

import static com.chitgo.pruebas.Entidades.conId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

import com.chitgo.auditoria.dominio.puertos.Auditor;
import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.menu.aplicacion.dto.CambioDeDisponibilidadDto;
import com.chitgo.menu.aplicacion.dto.PlatoDisponibilidadDto;
import com.chitgo.menu.dominio.CategoriaMenu;
import com.chitgo.menu.dominio.Plato;
import com.chitgo.menu.dominio.puertos.AvisoDeDisponibilidad;
import com.chitgo.menu.dominio.puertos.PedidosConPlato;
import com.chitgo.menu.dominio.puertos.PedidosConPlato.PedidoAfectado;
import com.chitgo.menu.dominio.puertos.PedidosConPlato.PlatoDelPedido;
import com.chitgo.menu.dominio.puertos.PlatoRepository;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

class CambiarDisponibilidadServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-05T20:00:00Z");
    private static final UsuarioAutenticado CARLA =
            new UsuarioAutenticado(3L, "CAJA", 1L, false, AHORA.plusSeconds(3600));
    private static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(2L, "ADMIN", 1L, false, AHORA.plusSeconds(600));

    private final PlatoRepository platos = mock(PlatoRepository.class);
    private final PedidosConPlato pedidosConPlato = mock(PedidosConPlato.class);
    private final AvisoDeDisponibilidad aviso = mock(AvisoDeDisponibilidad.class);
    private final Auditor auditor = mock(Auditor.class);
    private final CategoriaMenu principales = conId(new CategoriaMenu(1L, "Principales", (short) 1), 1L);
    private final CategoriaMenu postres = conId(new CategoriaMenu(1L, "Postres", (short) 2), 2L);
    private final Plato milanesa = conId(new Plato(1L, principales, "Milanesa napolitana", null,
            new BigDecimal("9500.00"), null), 1L);
    private final PlatoDelPedido unaMilanesa = new PlatoDelPedido(1L, "Milanesa napolitana", 1);
    /** CP-23-02: dos en espera y uno aprobado con Milanesa, uno también con Flan. */
    private final List<PedidoAfectado> tresConMilanesa = List.of(
            new PedidoAfectado(10L, "1", "PENDIENTE_APROBACION", List.of(unaMilanesa)),
            new PedidoAfectado(11L, "2", "PENDIENTE_APROBACION",
                    List.of(new PlatoDelPedido(1L, "Milanesa napolitana", 2), new PlatoDelPedido(3L, "Flan casero", 1))),
            new PedidoAfectado(12L, "3", "APROBADO", List.of(unaMilanesa)));
    private CambiarDisponibilidadService servicio;

    @BeforeEach
    void preparar() {
        servicio = new CambiarDisponibilidadService(platos, pedidosConPlato, aviso, auditor);
        when(platos.buscarPorId(1L, 1L)).thenReturn(Optional.of(milanesa));
        when(platos.guardar(any(Plato.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pedidosConPlato.afectadosPor(1L, 1L)).thenReturn(tresConMilanesa);
    }

    @Test
    void apagar_el_plato_guarda_audita_avisa_y_devuelve_los_afectados() {
        CambioDeDisponibilidadDto respuesta = servicio.cambiar(CARLA, 1L, false);

        assertEquals(1L, respuesta.platoId());
        assertFalse(respuesta.disponible());
        assertEquals(tresConMilanesa, respuesta.afectados());
        assertFalse(milanesa.isActivo());
        verify(platos).guardar(milanesa);
        verify(auditor).registrar(1L, 3L, "CAMBIAR_DISPONIBILIDAD", "PLATO", 1L,
                Map.of("disponible", true), Map.of("disponible", false));
        verify(aviso).platoCambio(1L, 1L, false);
    }

    @Test
    void sin_pedidos_con_el_plato_los_afectados_van_vacios() {
        when(pedidosConPlato.afectadosPor(1L, 1L)).thenReturn(List.of());

        CambioDeDisponibilidadDto respuesta = servicio.cambiar(CARLA, 1L, false);

        assertTrue(respuesta.afectados().isEmpty());
        verify(auditor).registrar(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void el_admin_tambien_puede_y_queda_el_en_la_auditoria() {
        servicio.cambiar(ADMIN, 1L, false);

        verify(auditor).registrar(1L, 2L, "CAMBIAR_DISPONIBILIDAD", "PLATO", 1L,
                Map.of("disponible", true), Map.of("disponible", false));
    }

    @Test
    void volver_a_disponible_audita_avisa_y_no_busca_afectados() {
        milanesa.cambiarDisponibilidad(false);

        CambioDeDisponibilidadDto respuesta = servicio.cambiar(CARLA, 1L, true);

        assertTrue(respuesta.disponible());
        assertTrue(respuesta.afectados().isEmpty());
        assertTrue(milanesa.isActivo());
        verify(platos).guardar(milanesa);
        verify(auditor).registrar(1L, 3L, "CAMBIAR_DISPONIBILIDAD", "PLATO", 1L,
                Map.of("disponible", false), Map.of("disponible", true));
        verify(aviso).platoCambio(1L, 1L, true);
        verifyNoInteractions(pedidosConPlato);
    }

    @Test
    void apagar_uno_ya_apagado_no_guarda_audita_ni_avisa_pero_trae_los_afectados_de_ahora() {
        milanesa.cambiarDisponibilidad(false);

        CambioDeDisponibilidadDto respuesta = servicio.cambiar(CARLA, 1L, false);

        assertFalse(respuesta.disponible());
        assertEquals(tresConMilanesa, respuesta.afectados());
        verify(platos, never()).guardar(any());
        verifyNoInteractions(auditor, aviso);
    }

    @Test
    void prender_uno_ya_disponible_no_guarda_audita_ni_avisa() {
        CambioDeDisponibilidadDto respuesta = servicio.cambiar(CARLA, 1L, true);

        assertTrue(respuesta.disponible());
        assertTrue(respuesta.afectados().isEmpty());
        verify(platos, never()).guardar(any());
        verifyNoInteractions(auditor, pedidosConPlato, aviso);
    }

    @Test
    void un_plato_de_otro_local_da_404_y_no_cambia_nada() {
        when(platos.buscarPorId(50L, 1L)).thenReturn(Optional.empty());

        NoEncontradoException e =
                assertThrows(NoEncontradoException.class, () -> servicio.cambiar(CARLA, 50L, false));

        assertEquals("El plato no existe", e.getMessage());
        verify(platos, never()).guardar(any());
        verifyNoInteractions(auditor, pedidosConPlato, aviso);
    }

    @Test
    void el_gerente_sin_local_no_entra() {
        UsuarioAutenticado gerente = new UsuarioAutenticado(1L, "GERENTE_DUENO", null, false, AHORA.plusSeconds(600));

        assertThrows(AccesoDenegadoException.class, () -> servicio.cambiar(gerente, 1L, false));
        assertThrows(AccesoDenegadoException.class, () -> servicio.listar(gerente));
        verify(platos, never()).buscarPorId(anyLong(), any());
        verifyNoInteractions(auditor, pedidosConPlato, aviso);
    }

    @Test
    void la_lista_de_caja_trae_tambien_los_apagados_en_el_orden_del_repositorio() {
        Plato flan = conId(new Plato(1L, postres, "Flan casero", null, new BigDecimal("4000.00"), null), 3L);
        flan.cambiarDisponibilidad(false);
        when(platos.listarDelLocal(1L)).thenReturn(List.of(milanesa, flan));

        List<PlatoDisponibilidadDto> lista = servicio.listar(CARLA);

        assertEquals(List.of(
                new PlatoDisponibilidadDto(1L, "Milanesa napolitana",
                        new PlatoDisponibilidadDto.CategoriaDto(1L, "Principales"), true),
                new PlatoDisponibilidadDto(3L, "Flan casero", new PlatoDisponibilidadDto.CategoriaDto(2L, "Postres"),
                        false)), lista);
    }
}
