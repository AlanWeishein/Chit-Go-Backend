package com.chitgo.usuarios.dominio;

import java.security.Principal;
import java.time.Instant;

/**
 * El usuario de la sesión, tal como viene en el token. Es el principal de Spring Security en el HTTP
 * y en el WebSocket: se recibe con {@code @AuthenticationPrincipal UsuarioAutenticado}.
 *
 * @param localId NULL para el Gerente/Dueño
 */
public record UsuarioAutenticado(Long usuarioId, String rol, Long localId, boolean debeCambiarCredencial,
        Instant expiraEn) implements Principal {

    public static UsuarioAutenticado desde(TokenLeido token) {
        DatosDeSesion datos = token.datos();
        return new UsuarioAutenticado(datos.usuarioId(), datos.rol(), datos.localId(),
                datos.debeCambiarCredencial(), token.expiraEn());
    }

    /** El id, para los destinos /user/... del WebSocket. */
    @Override
    public String getName() {
        return usuarioId.toString();
    }
}
