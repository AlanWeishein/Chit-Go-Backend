package com.chitgo.usuarios.dominio;

/** Bloqueado por intentos fallidos (RNF-07). Responde 423, también con el PIN correcto. */
public class UsuarioBloqueadoException extends RuntimeException {

    public UsuarioBloqueadoException() {
        super("Tu usuario está bloqueado. Pedile al Admin que te blanquee el PIN.");
    }
}
