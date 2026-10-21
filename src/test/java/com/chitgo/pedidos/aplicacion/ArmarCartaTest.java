package com.chitgo.pedidos.aplicacion;

import static com.chitgo.pruebas.Entidades.con;
import static com.chitgo.pruebas.Entidades.conId;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.chitgo.menu.dominio.Carta;
import com.chitgo.menu.dominio.CategoriaMenu;
import com.chitgo.menu.dominio.Etiqueta;
import com.chitgo.menu.dominio.Plato;
import com.chitgo.menu.dominio.PlatoDeCarta;
import com.chitgo.menu.dominio.PlatoEtiqueta;

/** Arma la carta a partir de las entidades, como la lee EntrarALaMesaService. */
class ArmarCartaTest {

    private static final CategoriaMenu PRINCIPALES = conId(new CategoriaMenu(1L, "Principales", (short) 1), 1L);
    private static final CategoriaMenu POSTRES = conId(new CategoriaMenu(1L, "Postres", (short) 2), 2L);
    private static final Etiqueta SIN_TACC = conId(new Etiqueta(1L, "Sin TACC"), 1L);

    private static Plato plato(long id, CategoriaMenu categoria, String nombre, boolean destacado, boolean sinTacc) {
        Plato plato = conId(new Plato(1L, categoria, nombre, null, new BigDecimal("1000.00"), null), id);
        con(plato, "destacado", destacado);
        if (sinTacc) {
            new PlatoEtiqueta(1L, plato, SIN_TACC);
        }
        return plato;
    }

    private static List<Plato> menu() {
        return List.of(
                plato(1, PRINCIPALES, "Milanesa napolitana", false, false),
                plato(3, POSTRES, "Flan casero", true, true),
                plato(2, PRINCIPALES, "Ensalada mixta", false, true));
    }

    private static List<String> nombres(Carta carta) {
        return carta.categorias().stream().flatMap(c -> c.platos().stream()).map(PlatoDeCarta::nombre).toList();
    }

    @Test
    void las_categorias_van_en_su_orden_y_los_platos_por_nombre() {
        Carta carta = EntrarALaMesaService.armarCarta(menu());
        assertEquals(List.of("Principales", "Postres"), carta.categorias().stream().map(c -> c.nombre()).toList());
        assertEquals(List.of("Ensalada mixta", "Milanesa napolitana", "Flan casero"), nombres(carta));
    }

    @Test
    void los_destacados_van_aparte_y_se_arman_con_sus_etiquetas() {
        Carta carta = EntrarALaMesaService.armarCarta(menu());
        assertEquals(List.of("Flan casero"), carta.destacados().stream().map(PlatoDeCarta::nombre).toList());
        assertEquals(List.of("Sin TACC"), carta.destacados().get(0).etiquetas());
    }

    @Test
    void CP_02_02_con_el_filtro_sin_tacc_quedan_la_ensalada_y_el_flan() {
        Carta carta = EntrarALaMesaService.armarCarta(menu()).filtrarPorEtiquetas(Set.of("sin-tacc"));
        assertEquals(List.of("Ensalada mixta", "Flan casero"), nombres(carta));
    }
}
