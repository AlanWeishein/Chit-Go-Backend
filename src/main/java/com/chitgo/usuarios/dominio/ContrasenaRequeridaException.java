package com.chitgo.usuarios.dominio;

/**
 * Un mozo quiere entrar con el PIN sin haber entrado con usuario y contraseña en este dispositivo en los
 * últimos 7 días. Responde 401 con un código para que el front muestre P-32. No suma intentos.
 */
public class ContrasenaRequeridaException extends RuntimeException {

    /** El campo "codigo" del ProblemDetail, que el front compara. */
    public static final String CODIGO = "CONTRASENA_REQUERIDA";

    public ContrasenaRequeridaException() {
        super("Ingresá tu usuario y contraseña.");
    }
}
