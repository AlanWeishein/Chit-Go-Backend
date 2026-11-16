package com.chitgo.menu.aplicacion;

import static com.chitgo.pruebas.Entidades.conId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chitgo.auditoria.dominio.puertos.Auditor;
import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.compartido.dominio.SolicitudInvalidaException;
import com.chitgo.menu.aplicacion.dto.CategoriaAdminDto;
import com.chitgo.menu.dominio.CategoriaConPlatosException;
import com.chitgo.menu.dominio.CategoriaMenu;
import com.chitgo.menu.dominio.PlatosDeCategoria;
import com.chitgo.menu.dominio.puertos.CategoriaMenuRepository;
import com.chitgo.menu.dominio.puertos.PlatoRepository;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

class GestionarCategoriasServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-02T20:00:00Z");
    private static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(2L, "ADMIN", 1L, false, AHORA.plusSeconds(600));
    private static final String ORDEN_INCOMPLETO =
            "El orden tiene que traer todas las categorías del local, una vez cada una";

    private final CategoriaMenuRepository categorias = mock(CategoriaMenuRepository.class);
    private final PlatoRepository platos = mock(PlatoRepository.class);
    private final Auditor auditor = mock(Auditor.class);
    private CategoriaMenu principales;
    private CategoriaMenu postres;
    private GestionarCategoriasService servicio;

    /** Las de la seed: Principales (1) y Postres (2), con 2 platos y 1. */
    @BeforeEach
    void preparar() {
        servicio = new GestionarCategoriasService(categorias, platos, auditor);
        principales = conId(new CategoriaMenu(1L, "Principales", (short) 1), 1L);
        postres = conId(new CategoriaMenu(1L, "Postres", (short) 2), 2L);
        when(categorias.listarDelLocal(1L)).thenReturn(List.of(principales, postres));
        when(categorias.buscarPorId(1L, 1L)).thenReturn(Optional.of(principales));
        when(categorias.buscarPorId(2L, 1L)).thenReturn(Optional.of(postres));
        when(categorias.guardar(any(CategoriaMenu.class))).thenAnswer(inv -> {
            CategoriaMenu categoria = inv.getArgument(0);
            return categoria.getId() == null ? conId(categoria, 30L) : categoria;
        });
        when(platos.contarPorCategoria(1L)).thenReturn(Map.of(
                1L, new PlatosDeCategoria(2, 0),
                2L, new PlatosDeCategoria(1, 0)));
    }

    @Test
    void listar_trae_las_del_local_en_su_orden_con_sus_platos_y_las_vacias_en_cero() {
        CategoriaMenu entradas = conId(new CategoriaMenu(1L, "Entradas", (short) 3), 3L);
        when(categorias.listarDelLocal(1L)).thenReturn(List.of(principales, postres, entradas));
        when(platos.contarPorCategoria(1L)).thenReturn(Map.of(
                1L, new PlatosDeCategoria(2, 0),
                2L, new PlatosDeCategoria(1, 1)));

        List<CategoriaAdminDto> lista = servicio.listar(ADMIN);

        assertEquals(List.of(new CategoriaAdminDto(1L, "Principales", 2, 0), new CategoriaAdminDto(2L, "Postres", 1, 1),
                new CategoriaAdminDto(3L, "Entradas", 0, 0)), lista);
    }

    @Test
    void crear_la_pone_al_final_del_orden_y_audita_crear_categoria() {
        CategoriaAdminDto dto = servicio.crear(ADMIN, "  Entradas ");

        assertEquals(new CategoriaAdminDto(30L, "Entradas", 0, 0), dto);
        verify(categorias).guardar(argThat(c -> c.getOrden() == 3));
        verify(auditor).registrar(1L, 2L, "CREAR_CATEGORIA", "CATEGORIA_MENU", 30L, null, Map.of("nombre", "Entradas"));
    }

    @Test
    void la_primera_categoria_del_local_va_en_el_orden_1() {
        when(categorias.listarDelLocal(1L)).thenReturn(List.of());

        servicio.crear(ADMIN, "Entradas");

        verify(categorias).guardar(argThat(c -> c.getOrden() == 1));
    }

    @Test
    void crear_sin_nombre_es_400_y_no_guarda_ni_audita() {
        SolicitudInvalidaException e = assertThrows(SolicitudInvalidaException.class, () -> servicio.crear(ADMIN, " "));

        assertEquals("Poné un nombre", e.getMessage());
        verify(categorias, never()).guardar(any());
        verifyNoInteractions(auditor);
    }

    @Test
    void renombrar_audita_el_nombre_anterior_y_el_nuevo() {
        CategoriaAdminDto dto = servicio.renombrar(ADMIN, 2L, "Postres caseros");

        assertEquals(new CategoriaAdminDto(2L, "Postres caseros", 1, 0), dto);
        verify(categorias).guardar(postres);
        verify(auditor).registrar(1L, 2L, "EDITAR_CATEGORIA", "CATEGORIA_MENU", 2L, Map.of("nombre", "Postres"),
                Map.of("nombre", "Postres caseros"));
    }

    @Test
    void renombrar_con_el_mismo_nombre_no_guarda_ni_audita() {
        servicio.renombrar(ADMIN, 2L, "Postres ");

        verify(categorias, never()).guardar(any());
        verifyNoInteractions(auditor);
    }

    @Test
    void renombrar_sin_nombre_es_400_y_deja_el_que_tenia() {
        SolicitudInvalidaException e = assertThrows(SolicitudInvalidaException.class,
                () -> servicio.renombrar(ADMIN, 2L, null));

        assertEquals("Poné un nombre", e.getMessage());
        assertEquals("Postres", postres.getNombre());
        verifyNoInteractions(auditor);
    }

    @Test
    void renombrar_una_de_otro_local_da_404_como_una_que_no_existe() {
        when(categorias.buscarPorId(9L, 1L)).thenReturn(Optional.empty());

        NoEncontradoException e = assertThrows(NoEncontradoException.class,
                () -> servicio.renombrar(ADMIN, 9L, "Entradas"));

        assertEquals("La categoría no existe", e.getMessage());
        verifyNoInteractions(auditor);
    }

    @Test
    void ordenar_cambia_el_orden_de_todas_y_audita_el_anterior_y_el_nuevo() {
        servicio.ordenar(ADMIN, List.of(2L, 1L));

        assertEquals(1, postres.getOrden());
        assertEquals(2, principales.getOrden());
        verify(auditor).registrar(1L, 2L, "ORDENAR_CATEGORIAS", "CATEGORIA_MENU", null, Map.of("ids", List.of(1L, 2L)),
                Map.of("ids", List.of(2L, 1L)));
    }

    @Test
    void ordenar_con_el_mismo_orden_no_guarda_ni_audita() {
        servicio.ordenar(ADMIN, List.of(1L, 2L));

        verify(categorias, never()).guardar(any());
        verifyNoInteractions(auditor);
    }

    @Test
    void ordenar_con_una_categoria_de_menos_es_400_y_no_cambia_nada() {
        assertOrdenInvalido(List.of(2L));
    }

    @Test
    void ordenar_con_una_categoria_de_mas_que_no_existe_es_400() {
        assertOrdenInvalido(List.of(2L, 1L, 3L));
    }

    @Test
    void ordenar_con_una_categoria_de_otro_local_en_lugar_de_una_del_local_es_400() {
        // La 9 es de El Puerto: para este local no existe, igual que si el id fuera inventado (RNF-10).
        assertOrdenInvalido(List.of(2L, 9L));
    }

    @Test
    void ordenar_con_una_categoria_repetida_es_400() {
        assertOrdenInvalido(List.of(2L, 1L, 2L));
    }

    @Test
    void ordenar_con_la_lista_vacia_es_400_si_el_local_tiene_categorias() {
        assertOrdenInvalido(List.of());
    }

    private void assertOrdenInvalido(List<Long> ids) {
        SolicitudInvalidaException e = assertThrows(SolicitudInvalidaException.class, () -> servicio.ordenar(ADMIN, ids));

        assertEquals(ORDEN_INCOMPLETO, e.getMessage());
        assertEquals(1, principales.getOrden());
        assertEquals(2, postres.getOrden());
        verify(categorias, never()).guardar(any());
        verifyNoInteractions(auditor);
    }

    @Test
    void borrar_una_sin_platos_la_borra_y_audita_borrar_categoria() {
        CategoriaMenu entradas = conId(new CategoriaMenu(1L, "Entradas", (short) 3), 3L);
        when(categorias.buscarPorId(3L, 1L)).thenReturn(Optional.of(entradas));

        servicio.borrar(ADMIN, 3L);

        verify(categorias).borrar(entradas);
        verify(auditor).registrar(1L, 2L, "BORRAR_CATEGORIA", "CATEGORIA_MENU", 3L, Map.of("nombre", "Entradas"), null);
    }

    @Test
    void borrar_una_con_platos_disponibles_es_409_con_cuantos_son() {
        CategoriaConPlatosException e = assertThrows(CategoriaConPlatosException.class, () -> servicio.borrar(ADMIN, 1L));

        assertEquals("La categoría tiene 2 platos. Movelos a otra antes de borrarla", e.getMessage());
        verify(categorias, never()).borrar(any());
        verifyNoInteractions(auditor);
    }

    @Test
    void borrar_una_con_un_solo_plato_fuera_de_carta_es_409_aunque_en_la_carta_no_se_vea() {
        when(platos.contarPorCategoria(1L)).thenReturn(Map.of(2L, new PlatosDeCategoria(1, 1)));

        CategoriaConPlatosException e = assertThrows(CategoriaConPlatosException.class, () -> servicio.borrar(ADMIN, 2L));

        assertEquals("La categoría tiene 1 plato (1 fuera de carta). Movelos a otra antes de borrarla", e.getMessage());
        verify(categorias, never()).borrar(any());
        verifyNoInteractions(auditor);
    }

    @Test
    void borrar_una_con_platos_y_alguno_fuera_de_carta_dice_cuantos_de_cada_uno() {
        when(platos.contarPorCategoria(1L)).thenReturn(Map.of(1L, new PlatosDeCategoria(3, 1)));

        CategoriaConPlatosException e = assertThrows(CategoriaConPlatosException.class, () -> servicio.borrar(ADMIN, 1L));

        assertEquals("La categoría tiene 3 platos (1 fuera de carta). Movelos a otra antes de borrarla", e.getMessage());
    }

    @Test
    void si_la_fk_frena_el_borrado_porque_entro_un_plato_justo_antes_es_409_y_no_audita() {
        CategoriaMenu entradas = conId(new CategoriaMenu(1L, "Entradas", (short) 3), 3L);
        when(categorias.buscarPorId(3L, 1L)).thenReturn(Optional.of(entradas));
        doThrow(new CategoriaConPlatosException()).when(categorias).borrar(entradas);

        CategoriaConPlatosException e = assertThrows(CategoriaConPlatosException.class, () -> servicio.borrar(ADMIN, 3L));

        assertEquals("La categoría tiene platos. Movelos a otra antes de borrarla", e.getMessage());
        verifyNoInteractions(auditor);
    }

    @Test
    void borrar_una_de_otro_local_da_404() {
        when(categorias.buscarPorId(9L, 1L)).thenReturn(Optional.empty());

        assertThrows(NoEncontradoException.class, () -> servicio.borrar(ADMIN, 9L));

        verify(categorias, never()).borrar(any());
        verifyNoInteractions(auditor);
    }

    @Test
    void el_gerente_dueno_no_tiene_local_y_no_entra() {
        UsuarioAutenticado gerente = new UsuarioAutenticado(9L, "GERENTE_DUENO", null, false, AHORA.plusSeconds(600));

        assertThrows(AccesoDenegadoException.class, () -> servicio.listar(gerente));
        assertThrows(AccesoDenegadoException.class, () -> servicio.ordenar(gerente, List.of(1L, 2L)));
        verifyNoInteractions(auditor);
    }
}
