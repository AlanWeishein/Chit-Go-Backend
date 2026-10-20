package com.chitgo.compartido.dominio;

/** Un dato del pedido no cumple la regla (cantidad, largo de la observación...). Responde 400. */
public class SolicitudInvalidaException extends RuntimeException {

    public SolicitudInvalidaException(String mensaje) {
        super(mensaje);
    }
}
