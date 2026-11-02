package com.chitgo.menu.infraestructura.web;

import java.util.List;

import jakarta.validation.constraints.NotNull;

/** El cuerpo de PUT /admin/categorias/orden: los ids de todas las categorías del local, en el orden nuevo. */
public record OrdenDeCategoriasRequest(@NotNull List<@NotNull Long> ids) {
}
