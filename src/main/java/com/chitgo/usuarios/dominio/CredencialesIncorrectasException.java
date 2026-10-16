package com.chitgo.usuarios.dominio;

/**
 * Cualquier falla del login con email: el email no existe, la contraseña no coincide, o el usuario está
 * bloqueado, dado de baja o no entra con email. Responde 401 con el mismo mensaje, sin decir qué falló.
 */
public class CredencialesIncorrectasException extends RuntimeException {

    public CredencialesIncorrectasException() {
        super("Email o contraseña incorrectos");
    }
}
