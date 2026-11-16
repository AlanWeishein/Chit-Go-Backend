package com.chitgo.menu.aplicacion.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lo que manda el Admin al dar de alta o editar un plato. Nombre, precio e imagen los valida Plato.
 *
 * @param etiquetaIds las etiquetas que tiene que quedar teniendo, enteras; NULL es ninguna
 */
public record DatosDePlato(String nombre, String descripcion, BigDecimal precio, Long categoriaId, String imagenUrl,
                           List<Long> etiquetaIds) {

    public DatosDePlato {
        etiquetaIds = etiquetaIds == null ? List.of() : List.copyOf(etiquetaIds);
    }
}
