package com.chitgo.menu.infraestructura.web;

import jakarta.validation.constraints.Size;

/**
 * El cuerpo de POST /admin/etiquetas y de PUT /admin/etiquetas/{id}. Acá va solo el largo del esquema, con el 400
 * genérico. Sin nombre lo valida Etiqueta, con el mensaje de HU-12.
 */
public record EtiquetaRequest(@Size(max = 60) String nombre) {
}
