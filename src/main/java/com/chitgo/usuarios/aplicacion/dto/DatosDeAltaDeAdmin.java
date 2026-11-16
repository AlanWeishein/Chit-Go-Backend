package com.chitgo.usuarios.aplicacion.dto;

/** Lo que manda el Gerente/Dueño para crear un Admin. La contraseña es la provisoria. */
public record DatosDeAltaDeAdmin(String nombre, String email, String password) {
}
