package com.chitgo.usuarios.dominio;

/**
 * El usuario entró con una credencial provisoria (primer ingreso o blanqueo) y quiere hacer otra cosa antes de
 * elegir la suya (CU-12, 1a). Responde 403 con un código para que el front muestre P-29, P-30 o P-31.
 */
public class CredencialProvisoriaException extends RuntimeException {

    /** El campo "codigo" del ProblemDetail, que el front compara. */
    public static final String CODIGO = "CREDENCIAL_PROVISORIA";

    public CredencialProvisoriaException() {
        super("Elegí tu PIN o tu contraseña antes de seguir.");
    }
}
