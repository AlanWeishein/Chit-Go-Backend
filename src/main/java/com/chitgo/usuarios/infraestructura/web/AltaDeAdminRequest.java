package com.chitgo.usuarios.infraestructura.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import com.chitgo.usuarios.aplicacion.dto.Contrasena;
import com.chitgo.usuarios.aplicacion.dto.DatosDeAltaDeAdmin;

/**
 * El cuerpo de POST /gerente/admins. Acá van el formato del email y de la contraseña y los largos del esquema, con el
 * 400 genérico. Lo que falta lo controla el Service, con un mensaje que nombra el campo.
 */
public record AltaDeAdminRequest(@Size(max = 120) String nombre, @Email @Size(max = 160) String email,
                                 @Contrasena String password) {

    public AltaDeAdminRequest {
        // Como el login: sin los espacios del borde, y sin dejar pasar uno largo recortándolo.
        if (email != null && email.length() <= 160) {
            email = email.strip();
        }
    }

    DatosDeAltaDeAdmin aDatos() {
        return new DatosDeAltaDeAdmin(nombre, email, password);
    }
}
