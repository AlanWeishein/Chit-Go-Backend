package com.chitgo.menu.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class CartaTest {

    private static PlatoDeCarta plato(long id, String nombre, boolean destacado, String... etiquetas) {
        return new PlatoDeCarta(id, nombre, null, new BigDecimal("1000.00"), null, destacado, List.of(etiquetas));
    }

    private static Carta carta() {
        return Carta.de(List.of(
                new CategoriaDeCarta(1L, "Principales", 1, List.of(
                        plato(1, "Milanesa napolitana", false),
                        plato(2, "Ensalada mixta", true, "Sin TACC", "Vegano"))),
                new CategoriaDeCarta(2L, "Postres", 2, List.of(plato(3, "Flan", false, "Sin TACC")))));
    }

    private static List<String> nombres(Carta carta) {
        return carta.categorias().stream().flatMap(c -> c.platos().stream()).map(PlatoDeCarta::nombre).toList();
    }

    @Test
    void CP_02_02_filtrar_por_sin_tacc_deja_solo_la_ensalada_y_el_flan() {
        assertEquals(List.of("Ensalada mixta", "Flan"), nombres(carta().filtrarPorEtiquetas(Set.of("sin-tacc"))));
    }

    @Test
    void la_etiqueta_se_compara_sin_importar_mayusculas_ni_espacios() {
        assertEquals(List.of("Ensalada mixta", "Flan"), nombres(carta().filtrarPorEtiquetas(Set.of("Sin TACC"))));
    }

    @Test
    void con_varias_etiquetas_exige_todas() {
        assertEquals(List.of("Ensalada mixta"), nombres(carta().filtrarPorEtiquetas(Set.of("sin-tacc", "vegano"))));
    }

    @Test
    void una_categoria_sin_platos_despues_del_filtro_no_aparece() {
        assertEquals(1, carta().filtrarPorEtiquetas(Set.of("vegano")).categorias().size());
    }

    @Test
    void sin_filtro_devuelve_todo_y_los_destacados_van_aparte() {
        Carta c = carta();
        assertEquals(3, nombres(c).size());
        assertEquals(List.of("Ensalada mixta"), c.destacados().stream().map(PlatoDeCarta::nombre).toList());
    }

    @Test
    void ningun_plato_cumple_el_filtro() {
        assertEquals(0, carta().filtrarPorEtiquetas(Set.of("sin-lactosa")).categorias().size());
    }
}
