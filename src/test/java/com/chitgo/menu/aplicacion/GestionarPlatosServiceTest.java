package com.chitgo.menu.aplicacion;

import static com.chitgo.pruebas.Entidades.con;
import static com.chitgo.pruebas.Entidades.conId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.chitgo.auditoria.dominio.puertos.Auditor;
import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.compartido.dominio.SolicitudInvalidaException;
import com.chitgo.menu.aplicacion.dto.DatosDePlato;
import com.chitgo.menu.aplicacion.dto.PlatoAdminDto;
import com.chitgo.menu.dominio.CategoriaMenu;
import com.chitgo.menu.dominio.Etiqueta;
import com.chitgo.menu.dominio.Plato;
import com.chitgo.menu.dominio.puertos.CategoriaMenuRepository;
import com.chitgo.menu.dominio.puertos.EtiquetaRepository;
import com.chitgo.menu.dominio.puertos.PlatoRepository;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

class GestionarPlatosServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-02T20:00:00Z");
    private static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(2L, "ADMIN", 1L, false, AHORA.plusSeconds(600));

    private final PlatoRepository platos = mock(PlatoRepository.class);
    private final CategoriaMenuRepository categorias = mock(CategoriaMenuRepository.class);
    private final EtiquetaRepository etiquetas = mock(EtiquetaRepository.class);
    private final Auditor auditor = mock(Auditor.class);
    private final CategoriaMenu principales = conId(new CategoriaMenu(1L, "Principales", (short) 1), 1L);
    private final CategoriaMenu postres = conId(new CategoriaMenu(1L, "Postres", (short) 2), 2L);
    private final Etiqueta sinTacc = conId(new Etiqueta(1L, "Sin TACC"), 1L);
    private final Etiqueta vegano = conId(new Etiqueta(1L, "Vegano"), 2L);
    private GestionarPlatosService servicio;

    @BeforeEach
    void preparar() {
        servicio = new GestionarPlatosService(platos, categorias, etiquetas, auditor);
        when(categorias.buscarPorId(1L, 1L)).thenReturn(Optional.of(principales));
        when(categorias.buscarPorId(2L, 1L)).thenReturn(Optional.of(postres));
        // Las del local 1 son Sin TACC y Vegano: cualquier otro id es de otro local o no existe.
        when(etiquetas.buscarPorIds(eq(1L), anyCollection())).thenAnswer(inv -> {
            Collection<Long> ids = inv.getArgument(1);
            return List.of(sinTacc, vegano).stream().filter(e -> ids.contains(e.getId())).toList();
        });
        when(platos.guardar(any(Plato.class))).thenAnswer(inv -> {
            Plato plato = inv.getArgument(0);
            return plato.getId() == null ? conId(plato, 20L) : plato;
        });
    }

    /** La Milanesa de la seed: $ 9.500, en Principales, con Sin TACC. */
    private Plato milanesaEnLaBase() {
        Plato milanesa = conId(new Plato(1L, principales, "Milanesa napolitana", "Con jamón y queso",
                new BigDecimal("9500.00"), null), 1L);
        milanesa.reemplazarEtiquetas(List.of(sinTacc));
        when(platos.buscarPorId(1L, 1L)).thenReturn(Optional.of(milanesa));
        return milanesa;
    }

    private static DatosDePlato milanesaCon(String precio) {
        return new DatosDePlato("Milanesa napolitana", "Con jamón y queso", new BigDecimal(precio), 1L, null, List.of(1L));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object>[] auditado(String accion, Long platoId) {
        ArgumentCaptor<Object> antes = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> despues = ArgumentCaptor.forClass(Object.class);
        verify(auditor).registrar(eq(1L), eq(2L), eq(accion), eq("PLATO"), eq(platoId), antes.capture(), despues.capture());
        return new Map[] {(Map<String, Object>) antes.getValue(), (Map<String, Object>) despues.getValue()};
    }

    private static Map<String, Object> mapa(Object... clavesYValores) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (int i = 0; i < clavesYValores.length; i += 2) {
            mapa.put((String) clavesYValores[i], clavesYValores[i + 1]);
        }
        return mapa;
    }

    @Test
    void CP_25_01_crear_guarda_el_plato_disponible_y_sin_destacar_y_audita_crear_plato_con_el_plato() {
        DatosDePlato datos = new DatosDePlato("Empanada de carne", "Al horno", new BigDecimal("1800"), 1L,
                "/img/empanada.jpg", List.of(2L, 1L, 2L));

        PlatoAdminDto dto = servicio.crear(ADMIN, datos);

        assertEquals(20L, dto.id());
        assertEquals("Empanada de carne", dto.nombre());
        assertEquals(new BigDecimal("1800.00"), dto.precio());
        assertEquals(new PlatoAdminDto.CategoriaDto(1L, "Principales"), dto.categoria());
        assertEquals("/img/empanada.jpg", dto.imagenUrl());
        assertTrue(dto.disponible());
        assertFalse(dto.destacado());
        assertEquals(List.of(new PlatoAdminDto.EtiquetaDto(1L, "Sin TACC"), new PlatoAdminDto.EtiquetaDto(2L, "Vegano")),
                dto.etiquetas());

        Map<String, Object>[] valores = auditado("CREAR_PLATO", 20L);
        assertNull(valores[0]);
        assertEquals(mapa("nombre", "Empanada de carne", "descripcion", "Al horno", "precio", new BigDecimal("1800.00"),
                "categoriaId", 1L, "imagenUrl", "/img/empanada.jpg", "etiquetaIds", List.of(1L, 2L),
                "destacado", false, "disponible", true), valores[1]);
    }

    @Test
    void CP_25_02_sin_nombre_no_guarda_ni_audita() {
        DatosDePlato datos = new DatosDePlato(" ", null, new BigDecimal("1800"), 1L, null, null);

        SolicitudInvalidaException e = assertThrows(SolicitudInvalidaException.class, () -> servicio.crear(ADMIN, datos));

        assertEquals("Poné un nombre", e.getMessage());
        verify(platos, never()).guardar(any());
        verifyNoInteractions(auditor);
    }

    @Test
    void CP_25_02_con_precio_negativo_no_guarda_ni_audita() {
        DatosDePlato datos = new DatosDePlato("Empanada", null, new BigDecimal("-100"), 1L, null, null);

        SolicitudInvalidaException e = assertThrows(SolicitudInvalidaException.class, () -> servicio.crear(ADMIN, datos));

        assertEquals("El precio tiene que ser mayor a cero", e.getMessage());
        verify(platos, never()).guardar(any());
        verifyNoInteractions(auditor);
    }

    @Test
    void una_imagen_http_es_400_y_no_guarda() {
        DatosDePlato datos = new DatosDePlato("Empanada", null, new BigDecimal("1800"), 1L,
                "http://fotos.example.com/empanada.jpg", null);

        SolicitudInvalidaException e = assertThrows(SolicitudInvalidaException.class, () -> servicio.crear(ADMIN, datos));

        assertEquals("La imagen tiene que ser un link https o una ruta del front", e.getMessage());
        verify(platos, never()).guardar(any());
        verifyNoInteractions(auditor);
    }

    @Test
    void la_imagen_https_y_la_ruta_del_front_se_guardan() {
        PlatoAdminDto conLink = servicio.crear(ADMIN, new DatosDePlato("Empanada", null, new BigDecimal("1800"), 1L,
                "https://fotos.example.com/empanada.jpg", null));
        PlatoAdminDto conRuta = servicio.crear(ADMIN, new DatosDePlato("Empanada", null, new BigDecimal("1800"), 1L,
                "/img/empanada.jpg", null));

        assertEquals("https://fotos.example.com/empanada.jpg", conLink.imagenUrl());
        assertEquals("/img/empanada.jpg", conRuta.imagenUrl());
    }

    @Test
    void una_categoria_de_otro_local_da_404_como_una_que_no_existe() {
        when(categorias.buscarPorId(9L, 1L)).thenReturn(Optional.empty());
        DatosDePlato datos = new DatosDePlato("Empanada", null, new BigDecimal("1800"), 9L, null, null);

        NoEncontradoException e = assertThrows(NoEncontradoException.class, () -> servicio.crear(ADMIN, datos));

        assertEquals("La categoría no existe", e.getMessage());
        verify(platos, never()).guardar(any());
        verifyNoInteractions(auditor);
    }

    @Test
    void una_etiqueta_de_otro_local_da_404_como_una_que_no_existe() {
        DatosDePlato datos = new DatosDePlato("Empanada", null, new BigDecimal("1800"), 1L, null, List.of(1L, 9L));

        NoEncontradoException e = assertThrows(NoEncontradoException.class, () -> servicio.crear(ADMIN, datos));

        assertEquals("La etiqueta no existe", e.getMessage());
        verify(platos, never()).guardar(any());
        verifyNoInteractions(auditor);
    }

    @Test
    void CP_25_03_un_cambio_de_precio_audita_solo_el_precio_anterior_y_el_nuevo() {
        Plato milanesa = milanesaEnLaBase();

        PlatoAdminDto dto = servicio.editar(ADMIN, 1L, milanesaCon("10500"));

        assertEquals(new BigDecimal("10500.00"), dto.precio());
        verify(platos).guardar(milanesa);
        Map<String, Object>[] valores = auditado("EDITAR_PLATO", 1L);
        assertEquals(Map.of("precio", new BigDecimal("9500.00")), valores[0]);
        assertEquals(Map.of("precio", new BigDecimal("10500.00")), valores[1]);
    }

    @Test
    void un_put_sin_cambios_no_guarda_ni_audita() {
        milanesaEnLaBase();

        PlatoAdminDto dto = servicio.editar(ADMIN, 1L, milanesaCon("9500"));

        assertEquals("Milanesa napolitana", dto.nombre());
        verify(platos, never()).guardar(any());
        verifyNoInteractions(auditor);
    }

    @Test
    void editar_la_categoria_las_etiquetas_y_la_imagen_audita_esos_campos() {
        milanesaEnLaBase();
        DatosDePlato datos = new DatosDePlato("Milanesa napolitana", "Con jamón y queso", new BigDecimal("9500"), 2L,
                "/img/milanesa.jpg", List.of(2L));

        PlatoAdminDto dto = servicio.editar(ADMIN, 1L, datos);

        assertEquals(new PlatoAdminDto.CategoriaDto(2L, "Postres"), dto.categoria());
        assertEquals(List.of(new PlatoAdminDto.EtiquetaDto(2L, "Vegano")), dto.etiquetas());
        Map<String, Object>[] valores = auditado("EDITAR_PLATO", 1L);
        assertEquals(mapa("categoriaId", 1L, "imagenUrl", null, "etiquetaIds", List.of(1L)), valores[0]);
        assertEquals(mapa("categoriaId", 2L, "imagenUrl", "/img/milanesa.jpg", "etiquetaIds", List.of(2L)), valores[1]);
    }

    @Test
    void editar_no_toca_destacado_ni_disponible() {
        Plato milanesa = milanesaEnLaBase();
        milanesa.marcarDestacado(true);

        PlatoAdminDto dto = servicio.editar(ADMIN, 1L, milanesaCon("10500"));

        assertTrue(dto.destacado());
        assertTrue(dto.disponible());
    }

    @Test
    void editar_con_una_categoria_de_otro_local_da_404_y_el_plato_no_cambia() {
        Plato milanesa = milanesaEnLaBase();
        when(categorias.buscarPorId(9L, 1L)).thenReturn(Optional.empty());
        DatosDePlato datos = new DatosDePlato("Milanesa a caballo", null, new BigDecimal("10500"), 9L, null, null);

        NoEncontradoException e = assertThrows(NoEncontradoException.class, () -> servicio.editar(ADMIN, 1L, datos));

        assertEquals("La categoría no existe", e.getMessage());
        assertEquals("Milanesa napolitana", milanesa.getNombre());
        assertEquals(new BigDecimal("9500.00"), milanesa.getPrecio());
        verifyNoInteractions(auditor);
    }

    @Test
    void editar_un_plato_de_otro_local_da_404_como_uno_que_no_existe() {
        when(platos.buscarPorId(50L, 1L)).thenReturn(Optional.empty());

        NoEncontradoException e = assertThrows(NoEncontradoException.class,
                () -> servicio.editar(ADMIN, 50L, milanesaCon("10500")));

        assertEquals("El plato no existe", e.getMessage());
        verifyNoInteractions(auditor);
    }

    @Test
    void CP_25_04_destacar_audita_destacar_plato_con_el_antes_y_el_despues() {
        Plato milanesa = milanesaEnLaBase();

        servicio.destacar(ADMIN, 1L, true);

        assertTrue(milanesa.isDestacado());
        verify(platos).guardar(milanesa);
        verify(auditor).registrar(1L, 2L, "DESTACAR_PLATO", "PLATO", 1L,
                Map.of("destacado", false), Map.of("destacado", true));
    }

    @Test
    void destacar_un_plato_que_ya_estaba_destacado_no_guarda_ni_audita() {
        Plato milanesa = milanesaEnLaBase();
        milanesa.marcarDestacado(true);

        servicio.destacar(ADMIN, 1L, true);

        assertTrue(milanesa.isDestacado());
        verify(platos, never()).guardar(any());
        verifyNoInteractions(auditor);
    }

    @Test
    void destacar_un_plato_de_otro_local_da_404() {
        when(platos.buscarPorId(50L, 1L)).thenReturn(Optional.empty());

        assertThrows(NoEncontradoException.class, () -> servicio.destacar(ADMIN, 50L, true));
        verifyNoInteractions(auditor);
    }

    @Test
    void listar_devuelve_tambien_los_fuera_de_carta_en_el_orden_del_repositorio() {
        Plato milanesa = milanesaEnLaBase();
        Plato flan = conId(new Plato(1L, postres, "Flan casero", null, new BigDecimal("4000"), null), 3L);
        flan.marcarDestacado(true);
        con(flan, "activo", false);
        when(platos.listarDelLocal(1L)).thenReturn(List.of(milanesa, flan));

        List<PlatoAdminDto> lista = servicio.listar(ADMIN);

        assertEquals(List.of(1L, 3L), lista.stream().map(PlatoAdminDto::id).toList());
        assertEquals(List.of(new PlatoAdminDto.EtiquetaDto(1L, "Sin TACC")), lista.get(0).etiquetas());
        assertTrue(lista.get(1).destacado());
        assertFalse(lista.get(1).disponible());
    }

    @Test
    void el_gerente_dueno_no_tiene_local_y_no_gestiona_la_carta() {
        UsuarioAutenticado gerente = new UsuarioAutenticado(1L, "GERENTE_DUENO", null, false, AHORA.plusSeconds(600));

        assertThrows(AccesoDenegadoException.class, () -> servicio.listar(gerente));
        assertThrows(AccesoDenegadoException.class, () -> servicio.crear(gerente, milanesaCon("9500")));
        assertThrows(AccesoDenegadoException.class, () -> servicio.editar(gerente, 1L, milanesaCon("9500")));
        assertThrows(AccesoDenegadoException.class, () -> servicio.destacar(gerente, 1L, true));
        verifyNoInteractions(platos, auditor);
    }

    @Test
    void la_auditoria_del_alta_guarda_null_en_la_descripcion_y_la_imagen_vacias() {
        servicio.crear(ADMIN, new DatosDePlato("Empanada", "", new BigDecimal("1800"), 1L, "  ", null));

        Map<String, Object>[] valores = auditado("CREAR_PLATO", 20L);
        assertTrue(valores[1].containsKey("descripcion"));
        assertNull(valores[1].get("descripcion"));
        assertNull(valores[1].get("imagenUrl"));
        assertEquals(List.of(), valores[1].get("etiquetaIds"));
    }
}
