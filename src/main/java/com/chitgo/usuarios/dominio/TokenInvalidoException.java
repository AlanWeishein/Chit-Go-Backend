package com.chitgo.usuarios.dominio;

/** El token está vencido, alterado o mal formado. Para el que llama es lo mismo: no hay sesión. */
public class TokenInvalidoException extends RuntimeException {

    public TokenInvalidoException(String mensaje) {
        super(mensaje);
    }

    public TokenInvalidoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
