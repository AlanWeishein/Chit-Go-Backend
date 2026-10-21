package com.chitgo.menu.dominio;

import java.util.Locale;

public final class Etiquetas {

    private Etiquetas() {
    }

    /** "Sin TACC" y "sin-tacc" son la misma etiqueta. */
    public static String normalizar(String etiqueta) {
        return etiqueta.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
    }
}
