package com.chitgo.compartido.dominio;

/** No hay una sesión válida: el usuario tiene que volver a entrar. Responde 401. */
public class SinSesionException extends RuntimeException {

    public SinSesionException(String mensaje) {
        super(mensaje);
    }
}
