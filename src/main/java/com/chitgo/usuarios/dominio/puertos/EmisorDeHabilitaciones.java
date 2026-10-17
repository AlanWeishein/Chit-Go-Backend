package com.chitgo.usuarios.dominio.puertos;

import com.chitgo.usuarios.dominio.DispositivoHabilitado;
import com.chitgo.usuarios.dominio.TokenInvalidoException;

/**
 * Emite y lee lo que va en la cookie de dispositivo del mozo. Es otro token que el de sesión: uno no sirve en
 * lugar del otro.
 */
public interface EmisorDeHabilitaciones {

    /** Token firmado que dice que ese mozo entró con usuario y contraseña ahora, y vence a los 7 días. */
    String emitir(Long usuarioId);

    /**
     * Valida la firma y el vencimiento.
     *
     * @throws TokenInvalidoException si está vencido, alterado, mal formado, le faltan datos o es un token
     *                                de sesión
     */
    DispositivoHabilitado leer(String token);
}
