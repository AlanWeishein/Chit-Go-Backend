package com.chitgo.usuarios.dominio;

/** El PIN o la contraseña que eligió no sirven. Responde 400 y no se guarda nada. */
public class CredencialNuevaInvalidaException extends RuntimeException {

    private CredencialNuevaInvalidaException(String mensaje) {
        super(mensaje);
    }

    /** Lo repitió distinto (CU-12, 4a). */
    public static CredencialNuevaInvalidaException noCoinciden() {
        return new CredencialNuevaInvalidaException("No coinciden");
    }

    /** Eligió el mismo que tenía (CU-12, 4b). */
    public static CredencialNuevaInvalidaException pinIgualAlAnterior() {
        return new CredencialNuevaInvalidaException("El PIN nuevo tiene que ser distinto del anterior.");
    }

    public static CredencialNuevaInvalidaException contrasenaIgualALaAnterior() {
        return new CredencialNuevaInvalidaException("La contraseña nueva tiene que ser distinta de la anterior.");
    }

    public static CredencialNuevaInvalidaException faltaElPin() {
        return new CredencialNuevaInvalidaException("Elegí tu PIN.");
    }

    public static CredencialNuevaInvalidaException faltaLaContrasena() {
        return new CredencialNuevaInvalidaException("Elegí tu contraseña.");
    }

    /** Mandó una credencial que su rol no usa: un PIN para el Admin, o una contraseña para Caja o Cocina. */
    public static CredencialNuevaInvalidaException noCorrespondeAlRol() {
        return new CredencialNuevaInvalidaException("Los datos enviados no son válidos.");
    }
}
