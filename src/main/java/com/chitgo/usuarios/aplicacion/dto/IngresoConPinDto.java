package com.chitgo.usuarios.aplicacion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Cuerpo de POST /auth/pin. El PIN, de 4 a 10 dígitos. */
public record IngresoConPinDto(
        @NotBlank @Size(max = 36) String tokenStaff,
        @NotNull Long usuarioId,
        @NotNull @Pattern(regexp = "\\d{4,10}") String pin) {
}
