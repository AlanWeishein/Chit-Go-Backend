package com.chitgo.compartido.dominio;

import java.util.List;

/** Resultado paginado sin dependencia de Spring Data. */
public record PaginaDe<T>(List<T> contenido, long total) { }
