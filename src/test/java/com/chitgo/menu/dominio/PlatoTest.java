package com.chitgo.menu.dominio;

import static com.chitgo.pruebas.Entidades.conId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.chitgo.compartido.dominio.SolicitudInvalidaException;

class PlatoTest {

    private static final CategoriaMenu PRINCIPALES = conId(new CategoriaMenu(1L, "Principales", (short) 1), 1L);
    private static final CategoriaMenu POSTRES = conId(new CategoriaMenu(1L, "Postres", (short) 2), 2L);
    private static final Etiqueta SIN_TACC = conId(new Etiqueta(1L, "Sin TACC"), 1L);
    private static final Etiqueta VEGANO = conId(new Etiqueta(1L, "Vegano"), 2L);
    private static final Etiqueta SIN_LACTOSA = conId(new Etiqueta(1L, "Sin lactosa"), 3L);

    private static Plato plato(String nombre, String precio, String imagenUrl) {
        return new Plato(1L, PRINCIPALES, nombre, null, precio == null ? null : new BigDecimal(precio), imagenUrl);
    }

    private static Plato milanesa() {
        return conId(new Plato(1L, PRINCIPALES, "Milanesa napolitana", "Con jamón y queso", new BigDecimal("9500.00"),
                "/img/milanesa.jpg"), 1L);
    }

    private static Set<Long> etiquetaIds(Plato plato) {
        return plato.getPlatoEtiquetas().stream().map(pe -> pe.getId().getEtiquetaId()).collect(Collectors.toSet());
    }

    @Test
    void nace_disponible_y_sin_destacar_con_el_nombre_sin_espacios_en_las_puntas() {
        Plato plato = new Plato(1L, PRINCIPALES, "  Empanada de carne ", "   ", new BigDecimal("1800"), null);

        assertEquals("Empanada de carne", plato.getNombre());
        assertNull(plato.getDescripcion());
        assertEquals(new BigDecimal("1800.00"), plato.getPrecio());
        assertTrue(plato.isActivo());
        assertFalse(plato.isDestacado());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void sin_nombre_es_pone_un_nombre(String nombre) {
        SolicitudInvalidaException e = assertThrows(SolicitudInvalidaException.class,
                () -> plato(nombre, "1800", null));

        assertEquals("Poné un nombre", e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-100"})
    void con_precio_cero_o_negativo_el_precio_tiene_que_ser_mayor_a_cero(String precio) {
        SolicitudInvalidaException e = assertThrows(SolicitudInvalidaException.class,
                () -> plato("Empanada", precio, null));

        assertEquals("El precio tiene que ser mayor a cero", e.getMessage());
    }

    @Test
    void sin_precio_el_precio_tiene_que_ser_mayor_a_cero() {
        SolicitudInvalidaException e = assertThrows(SolicitudInvalidaException.class,
                () -> plato("Empanada", null, null));

        assertEquals("El precio tiene que ser mayor a cero", e.getMessage());
    }

    @Test
    void con_mas_de_dos_decimales_es_400_pero_los_ceros_de_mas_no_cuentan() {
        assertThrows(SolicitudInvalidaException.class, () -> plato("Empanada", "1800.005", null));

        assertEquals(new BigDecimal("1800.50"), plato("Empanada", "1800.500", null).getPrecio());
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://fotos.example.com/milanesa.jpg", "HTTPS://fotos.example.com/a.jpg",
            "/img/milanesa.jpg", "/milanesa.jpg"})
    void la_imagen_puede_ser_un_link_https_o_una_ruta_del_front(String imagen) {
        assertEquals(imagen, plato("Milanesa", "9500", imagen).getImagenUrl());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void la_imagen_vacia_se_guarda_null(String imagen) {
        assertNull(plato("Milanesa", "9500", imagen).getImagenUrl());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://fotos.example.com/milanesa.jpg", "//fotos.example.com/milanesa.jpg",
            "/\\fotos.example.com/milanesa.jpg", "img/milanesa.jpg", "https://", "ftp://fotos.example.com/a.jpg",
            "javascript:alert(1)"})
    void otra_imagen_es_400(String imagen) {
        SolicitudInvalidaException e = assertThrows(SolicitudInvalidaException.class,
                () -> plato("Milanesa", "9500", imagen));

        assertEquals("La imagen tiene que ser un link https o una ruta del front", e.getMessage());
    }

    @Test
    void la_imagen_tiene_hasta_255_caracteres() {
        String hasta = "/" + "a".repeat(254);
        String pasada = "/" + "a".repeat(255);

        assertEquals(hasta, plato("Milanesa", "9500", hasta).getImagenUrl());
        assertThrows(SolicitudInvalidaException.class, () -> plato("Milanesa", "9500", pasada));
    }

    @Test
    void editar_cambia_los_datos_y_no_toca_destacado_ni_disponible() {
        Plato plato = milanesa();
        plato.marcarDestacado(true);

        plato.editar(POSTRES, "Milanesa a caballo", "Con huevo", new BigDecimal("10500"), null);

        assertSame(POSTRES, plato.getCategoria());
        assertEquals("Milanesa a caballo", plato.getNombre());
        assertEquals("Con huevo", plato.getDescripcion());
        assertEquals(new BigDecimal("10500.00"), plato.getPrecio());
        assertNull(plato.getImagenUrl());
        assertTrue(plato.isDestacado());
        assertTrue(plato.isActivo());
    }

    @Test
    void si_un_dato_no_vale_editar_no_cambia_nada() {
        Plato plato = milanesa();

        assertThrows(SolicitudInvalidaException.class,
                () -> plato.editar(POSTRES, "Milanesa a caballo", null, new BigDecimal("10500"), "http://x.com/a.jpg"));

        assertSame(PRINCIPALES, plato.getCategoria());
        assertEquals("Milanesa napolitana", plato.getNombre());
        assertEquals("Con jamón y queso", plato.getDescripcion());
        assertEquals(new BigDecimal("9500.00"), plato.getPrecio());
        assertEquals("/img/milanesa.jpg", plato.getImagenUrl());
    }

    @Test
    void marcar_destacado_y_volver_a_sacarlo() {
        Plato plato = milanesa();

        plato.marcarDestacado(true);
        assertTrue(plato.isDestacado());

        plato.marcarDestacado(false);
        assertFalse(plato.isDestacado());
    }

    @Test
    void reemplazar_etiquetas_deja_exactamente_las_que_vienen_y_no_toca_las_que_siguen() {
        Plato plato = milanesa();
        plato.reemplazarEtiquetas(List.of(SIN_TACC, VEGANO));
        PlatoEtiqueta sinTacc = plato.getPlatoEtiquetas().stream()
                .filter(pe -> pe.getEtiqueta() == SIN_TACC).findFirst().orElseThrow();

        plato.reemplazarEtiquetas(List.of(SIN_TACC, SIN_LACTOSA));

        assertEquals(Set.of(1L, 3L), etiquetaIds(plato));
        assertTrue(plato.getPlatoEtiquetas().contains(sinTacc));
        assertTrue(plato.getPlatoEtiquetas().stream().allMatch(pe -> pe.getLocalId().equals(1L)));
    }

    @Test
    void reemplazar_por_ninguna_las_saca_todas_y_las_repetidas_van_una_vez() {
        Plato plato = milanesa();
        plato.reemplazarEtiquetas(List.of(VEGANO, VEGANO));
        assertEquals(1, plato.getPlatoEtiquetas().size());

        plato.reemplazarEtiquetas(List.of());

        assertTrue(plato.getPlatoEtiquetas().isEmpty());
    }

    @Test
    void un_plato_sin_guardar_no_puede_llevar_etiquetas() {
        Plato plato = plato("Empanada", "1800", null);

        assertThrows(IllegalStateException.class, () -> plato.reemplazarEtiquetas(List.of(SIN_TACC)));
    }
}
