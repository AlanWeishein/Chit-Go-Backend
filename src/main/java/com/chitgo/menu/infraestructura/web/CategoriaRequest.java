package com.chitgo.menu.infraestructura.web;

import jakarta.validation.constraints.Size;

/**
 * El cuerpo de POST /admin/categorias y de PUT /admin/categorias/{id}. Acá va solo el largo del esquema, con el
 * 400 genérico. Sin nombre lo valida CategoriaMenu, con el mensaje de HU-12.
 */
public record CategoriaRequest(@Size(max = 80) String nombre) {
}
