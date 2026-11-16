package com.chitgo.usuarios.dominio;

import java.util.List;

/** Una página de resultados y cuántos hay en total. Es del dominio para no depender de Spring Data. */
public record PaginaDe<T>(List<T> contenido, long total) {
}
