package com.chitgo.usuarios.aplicacion.dto;

/** Lo que manda el Admin al dar de alta. {@code nombreUsuario} y {@code password} son solo del Mozo. */
public record DatosDeAltaDeEmpleado(String nombre, String rol, String pin, String nombreUsuario, String password) {
}
