package com.chitgo.usuarios.aplicacion;

import com.chitgo.usuarios.aplicacion.dto.DispositivoDto;

/** Lo que devuelve el login del mozo con contraseña: el token va a la cookie de dispositivo, y el resto al cuerpo. */
public record ResultadoDeHabilitacion(String token, DispositivoDto dispositivo) {
}
