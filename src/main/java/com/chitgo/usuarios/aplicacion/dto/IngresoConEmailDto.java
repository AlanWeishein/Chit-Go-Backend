package com.chitgo.usuarios.aplicacion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de POST /auth/admin/login. El email no se valida como email: cualquier texto que no sea el de un
 * usuario da el 401 genérico, no un 400 (CP-11-07).
 */
public record IngresoConEmailDto(
        @NotBlank @Size(max = 160) String email,
        @NotNull @Contrasena String password) {
}
