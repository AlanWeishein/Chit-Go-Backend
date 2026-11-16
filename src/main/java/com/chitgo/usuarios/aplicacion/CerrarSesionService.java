package com.chitgo.usuarios.aplicacion;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.compartido.dominio.Reloj;
import com.chitgo.usuarios.dominio.puertos.UsuarioRepository;

/**
 * El logout (RF-33, CP-13-01): mueve el "válido desde" del usuario a ahora, así los tokens emitidos antes dan 401
 * aunque alguien los haya copiado. Cierra todos los dispositivos del usuario, no solo este (decidido el 20/10,
 * punto 7). Borrar la cookie lo sigue haciendo el controlador.
 */
@Service
public class CerrarSesionService {

    private final UsuarioRepository usuarios;
    private final Reloj reloj;

    CerrarSesionService(UsuarioRepository usuarios, Reloj reloj) {
        this.usuarios = usuarios;
        this.reloj = reloj;
    }

    /**
     * Si el usuario ya no existe no hay nada que cerrar: la cookie se borra igual. El cambio lo guarda el flush de
     * la transacción, como en los logins.
     */
    @Transactional
    public void cerrar(Long usuarioId) {
        usuarios.buscarPorId(usuarioId).ifPresent(usuario -> usuario.cerrarSesiones(reloj.ahora()));
    }
}
