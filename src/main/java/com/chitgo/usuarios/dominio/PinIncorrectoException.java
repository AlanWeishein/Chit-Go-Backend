package com.chitgo.usuarios.dominio;

/** El PIN no coincide con el del usuario elegido. Responde 401 y ya sumó un intento fallido. */
public class PinIncorrectoException extends RuntimeException {

    public PinIncorrectoException() {
        super("PIN incorrecto");
    }
}
