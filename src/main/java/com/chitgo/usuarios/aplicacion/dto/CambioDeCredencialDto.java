package com.chitgo.usuarios.aplicacion.dto;

import jakarta.validation.constraints.Pattern;

/**
 * Cuerpo de POST /auth/credencial (P-29, P-30, P-31 y P-32b). Cada credencial va dos veces. Qué campos lleva
 * depende del rol, y eso lo controla el Service: Mozo, Caja y Cocina mandan el PIN; el Admin y el
 * Gerente/Dueño, la contraseña; el mozo puede mandar también la contraseña.
 */
public record CambioDeCredencialDto(
        @Pattern(regexp = "\\d{4,10}") String pin,
        String pinRepetido,
        @Contrasena String password,
        String passwordRepetida) {
}
