package com.chitgo.menu.aplicacion.dto;

/**
 * Una categoría como la ve el Admin en P-14. Con platos no se deja borrar (409), aunque estén fuera de carta.
 *
 * @param platos       todos los que tiene, también los fuera de carta
 * @param fueraDeCarta de esos, los que tienen activo en false
 */
public record CategoriaAdminDto(Long id, String nombre, long platos, long fueraDeCarta) {
}
