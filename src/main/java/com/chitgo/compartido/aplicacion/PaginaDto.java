package com.chitgo.compartido.aplicacion;

import java.util.List;
import com.chitgo.compartido.dominio.SolicitudInvalidaException;

/** Contrato común de listados; page empieza en cero. */
public record PaginaDto<T>(List<T> contenido, int page, int size, long totalElementos, int totalPaginas) {
    public static void validar(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new SolicitudInvalidaException("page debe ser >= 0 y size debe estar entre 1 y 100");
        }
    }

    public static <T> PaginaDto<T> de(List<T> contenido, int page, int size, long total) {
        validar(page, size);
        return new PaginaDto<>(List.copyOf(contenido), page, size, total, (int) ((total + size - 1) / size));
    }
}
