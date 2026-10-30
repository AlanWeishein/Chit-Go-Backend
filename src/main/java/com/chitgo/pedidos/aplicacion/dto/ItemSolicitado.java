package com.chitgo.pedidos.aplicacion.dto;

import com.chitgo.compartido.dominio.SolicitudInvalidaException;

/** Un plato del carrito tal como lo manda la app. El precio nunca viene de la app. */
public record ItemSolicitado(Long platoId, int cantidad, String observacion) {

    public static final int CANTIDAD_MAXIMA = 999;
    public static final int LARGO_MAXIMO_OBSERVACION = 200;

    public ItemSolicitado {
        if (platoId == null) {
            throw new SolicitudInvalidaException("Falta el plato");
        }
        if (cantidad < 1 || cantidad > CANTIDAD_MAXIMA) {
            throw new SolicitudInvalidaException("La cantidad tiene que estar entre 1 y " + CANTIDAD_MAXIMA);
        }
        if (observacion != null && observacion.length() > LARGO_MAXIMO_OBSERVACION) {
            throw new SolicitudInvalidaException("La observación tiene hasta 200 caracteres");
        }
    }
}
