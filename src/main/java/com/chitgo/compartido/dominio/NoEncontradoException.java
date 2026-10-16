package com.chitgo.compartido.dominio;

/** No existe, o es de otro local: para el que pregunta es lo mismo. Responde 404. */
public class NoEncontradoException extends RuntimeException {

    public NoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
