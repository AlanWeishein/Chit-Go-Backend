package com.chitgo.usuarios.aplicacion.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Solo para aprovisionamiento operativo; no hay endpoint de alta de SuperAdmins. */
public record DatosDeAltaDeSuperadmin(
        @NotBlank(message = "Falta el nombre") @Size(max = 120, message = "El nombre es demasiado largo") String nombre,
        @NotBlank(message = "Falta el email") @Email(message = "El email no es válido")
        @Size(max = 160, message = "El email es demasiado largo") String email,
        @NotNull(message = "Falta la contraseña") @Contrasena String password) {
    public DatosDeAltaDeSuperadmin {
        if (nombre != null) nombre = nombre.strip();
        if (email != null && email.length() <= 160) email = email.strip();
    }
}
