package com.chitgo.menu.dominio;

import com.chitgo.compartido.dominio.ReglaDeNegocioException;

/** HU-12: ya hay una etiqueta con ese nombre en el local (CP-25-05). Responde 409. */
public class EtiquetaRepetidaException extends ReglaDeNegocioException {

    public static final String MENSAJE = "Esa etiqueta ya existe";

    public EtiquetaRepetidaException() {
        super(MENSAJE);
    }
}
