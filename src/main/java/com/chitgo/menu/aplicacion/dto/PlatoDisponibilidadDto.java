package com.chitgo.menu.aplicacion.dto;

/**
 * Un plato en la lista de disponibilidad de Caja (P-10), también si está apagado.
 *
 * @param disponible el {@code activo} del plato
 */
public record PlatoDisponibilidadDto(Long id, String nombre, CategoriaDto categoria, boolean disponible) {

    public record CategoriaDto(Long id, String nombre) {
    }
}
