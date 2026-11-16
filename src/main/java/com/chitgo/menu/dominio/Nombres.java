package com.chitgo.menu.dominio;

import com.chitgo.compartido.dominio.SolicitudInvalidaException;

/** El nombre de una categoría o de una etiqueta (HU-12). */
public final class Nombres {

    static final String SIN_NOMBRE = "Poné un nombre";

    private Nombres() {
    }

    /** Sin espacios en las puntas. Vacío, en blanco o NULL, 400 "Poné un nombre". */
    public static String obligatorio(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new SolicitudInvalidaException(SIN_NOMBRE);
        }
        return nombre.strip();
    }
}
