package com.chitgo.usuarios.dominio;

/**
 * Cualquier falla del usuario y contraseña del mozo: el usuario no existe en ese local, la contraseña no
 * coincide, o está bloqueado, dado de baja o no es mozo. Responde 401 con el mismo mensaje, sin decir qué
 * falló (P-32).
 */
public class UsuarioOContrasenaIncorrectosException extends RuntimeException {

    public UsuarioOContrasenaIncorrectosException() {
        super("Usuario o contraseña incorrectos");
    }
}
