package com.chitgo.usuarios.infraestructura.web;

import jakarta.validation.constraints.Size;

import com.chitgo.usuarios.aplicacion.dto.DatosDeEdicionDeEmpleado;

/** El cuerpo de PUT /admin/empleados/{id}. Lo que falta lo controla el Service. */
public record EdicionDeEmpleadoRequest(@Size(max = 120) String nombre, @Size(max = 40) String nombreUsuario) {

    DatosDeEdicionDeEmpleado aDatos() {
        return new DatosDeEdicionDeEmpleado(nombre, nombreUsuario);
    }
}
