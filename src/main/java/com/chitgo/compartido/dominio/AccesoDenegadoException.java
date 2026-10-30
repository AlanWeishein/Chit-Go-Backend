package com.chitgo.compartido.dominio;

/**
 * Hay sesión pero no alcanza: falta el permiso, o el cliente no es la sesión iniciadora, o el pedido es de
 * otra sesión. Responde 403.
 */
public class AccesoDenegadoException extends RuntimeException {

    public AccesoDenegadoException(String mensaje) {
        super(mensaje);
    }
}
