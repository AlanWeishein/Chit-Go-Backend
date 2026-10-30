package com.chitgo.usuarios.dominio;

/**
 * Llamó a /auth/credencial sin tener una credencial provisoria. Responde 409: cambiar el PIN o la contraseña
 * cuando ya es propia no está en el alcance, y pediría la actual.
 */
public class CredencialYaElegidaException extends RuntimeException {

    public CredencialYaElegidaException() {
        super("Tu credencial no es provisoria.");
    }
}
