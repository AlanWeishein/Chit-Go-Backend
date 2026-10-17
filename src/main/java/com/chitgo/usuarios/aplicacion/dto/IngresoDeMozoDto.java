package com.chitgo.usuarios.aplicacion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de POST /auth/mozo/login. El nombre de usuario, hasta 40 caracteres como la columna. Como con el email
 * del Admin, cualquier texto que no sea el de un mozo da el 401 genérico, no un 400.
 */
public record IngresoDeMozoDto(
        @NotBlank @Size(max = 36) String tokenStaff,
        @NotBlank @Size(max = 40) String nombreUsuario,
        @NotNull @Contrasena String password) {
}
