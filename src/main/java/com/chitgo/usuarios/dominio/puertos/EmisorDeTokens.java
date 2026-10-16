package com.chitgo.usuarios.dominio.puertos;

import java.time.Duration;

import com.chitgo.usuarios.dominio.DatosDeSesion;
import com.chitgo.usuarios.dominio.TokenInvalidoException;
import com.chitgo.usuarios.dominio.TokenLeido;

/** Emite y lee el token de sesión del personal. */
public interface EmisorDeTokens {

    /** Token firmado que vence cuando pasa la duración, contada desde ahora. */
    String emitir(DatosDeSesion datos, Duration duracion);

    /**
     * Valida la firma y el vencimiento, y devuelve lo que lleva el token.
     *
     * @throws TokenInvalidoException si está vencido, alterado, mal formado o le faltan datos
     */
    TokenLeido leer(String token);
}
