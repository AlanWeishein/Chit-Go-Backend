package com.chitgo.usuarios.aplicacion.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de POST /auth/admin/login. El email debe tener formato válido y respetar los 160 caracteres de
 * usuario.email. Los datos inválidos dan 400 antes de intentar autenticar.
 */
public record IngresoConEmailDto(
        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no es válido")
        @Size(max = 160, message = "El email es demasiado largo") String email,
        @NotNull @Contrasena String password) {

    public IngresoConEmailDto {
        // Mantiene el login con espacios al borde, sin permitir eludir el tope pegando espacios de más.
        if (email != null && email.length() <= 160) {
            email = email.strip();
        }
    }
}
