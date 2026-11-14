package com.chitgo.usuarios.aplicacion.dto;

import java.util.List;

/** Respuesta de los listados paginados del Admin. {@code page} empieza en 0. */
public record PaginaDto<T>(List<T> contenido, int page, int size, long totalElementos, int totalPaginas) {
}
