package com.chitgo.compartido.dominio;

/** Una regla de negocio no se cumple (transición de estado inválida, cuenta cerrada...). Responde 409. */
public class ReglaDeNegocioException extends RuntimeException {

    public ReglaDeNegocioException(String mensaje) {
        super(mensaje);
    }
}
