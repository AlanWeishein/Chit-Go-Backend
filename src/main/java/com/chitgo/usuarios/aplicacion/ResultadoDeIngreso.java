package com.chitgo.usuarios.aplicacion;

import java.time.Duration;

import com.chitgo.usuarios.aplicacion.dto.SesionDto;

/**
 * Lo que devuelve un login: el token y su duración van a la cookie, y la sesión al cuerpo de la respuesta,
 * para que el front no tenga que pedir /auth/sesion. La renovación de cada request devuelve lo mismo, pero
 * solo usa la cookie.
 */
public record ResultadoDeIngreso(String token, Duration duracion, SesionDto sesion) {
}
