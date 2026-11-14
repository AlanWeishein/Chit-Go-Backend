package com.chitgo.usuarios.infraestructura.web;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.chitgo.usuarios.aplicacion.dto.Contrasena;
import com.chitgo.usuarios.aplicacion.dto.DatosDeAltaDeEmpleado;

/**
 * El cuerpo de POST /admin/empleados. Acá van el formato del PIN y de la contraseña y los largos del esquema,
 * con el 400 genérico. Lo que falta o no corresponde al rol lo controla el Service, con un mensaje que nombra el
 * campo (ManejadorDeErrores pisa el mensaje de un {@code @Valid} que falla).
 */
public record AltaDeEmpleadoRequest(@Size(max = 120) String nombre, @Size(max = 30) String rol,
                                    @Pattern(regexp = "\\d{4,10}") String pin, @Size(max = 40) String nombreUsuario,
                                    @Contrasena String password) {

    DatosDeAltaDeEmpleado aDatos() {
        return new DatosDeAltaDeEmpleado(nombre, rol, pin, nombreUsuario, password);
    }
}
