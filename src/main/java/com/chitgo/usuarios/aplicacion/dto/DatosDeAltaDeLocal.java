package com.chitgo.usuarios.aplicacion.dto;

import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Lo que recibe el script de alta del local (HU-16). Los largos son los del esquema, y el email lleva la misma regla
 * que el login (IngresoConEmailDto): si no, se podría crear un Gerente/Dueño que después no puede entrar.
 *
 * <p>Los textos llegan sin los espacios del borde. La dirección es opcional, como en la tabla.
 */
public record DatosDeAltaDeLocal(
        @NotBlank(message = "Falta el nombre del local")
        @Size(max = 120, message = "El nombre del local es demasiado largo") String nombreLocal,
        @Size(max = 255, message = "La dirección es demasiado larga") String direccion,
        @NotBlank(message = "Falta el nombre del Gerente/Dueño")
        @Size(max = 120, message = "El nombre del Gerente/Dueño es demasiado largo") String nombreGerente,
        @NotBlank(message = "Falta el email")
        @Email(message = "El email no es válido")
        @Size(max = 160, message = "El email es demasiado largo") String email,
        @NotNull(message = "Falta la contraseña") @Contrasena String password) {

    /** El orden en que se muestran los errores, el mismo de los campos. */
    public static final List<String> CAMPOS = List.of("nombreLocal", "direccion", "nombreGerente", "email", "password");

    public DatosDeAltaDeLocal {
        nombreLocal = sinBordes(nombreLocal);
        direccion = sinBordes(direccion);
        if (direccion != null && direccion.isEmpty()) {
            direccion = null;
        }
        nombreGerente = sinBordes(nombreGerente);
        // Como el login: sin dejar pasar uno largo recortándolo.
        if (email != null && email.length() <= 160) {
            email = email.strip();
        }
    }

    private static String sinBordes(String texto) {
        return texto == null ? null : texto.strip();
    }
}
