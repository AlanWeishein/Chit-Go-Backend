package com.chitgo.menu.aplicacion.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Un plato como lo ve el Admin en P-14, también si está fuera de carta.
 *
 * @param disponible el {@code activo} del plato: en false está fuera de carta (09/10)
 * @param etiquetas  ordenadas por nombre
 */
public record PlatoAdminDto(Long id, String nombre, String descripcion, BigDecimal precio, CategoriaDto categoria,
                            String imagenUrl, boolean destacado, boolean disponible, List<EtiquetaDto> etiquetas) {

    public record CategoriaDto(Long id, String nombre) {
    }

    public record EtiquetaDto(Long id, String nombre) {
    }
}
