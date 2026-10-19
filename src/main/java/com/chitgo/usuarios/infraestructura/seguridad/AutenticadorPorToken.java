package com.chitgo.usuarios.infraestructura.seguridad;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import com.chitgo.usuarios.dominio.TokenInvalidoException;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;
import com.chitgo.usuarios.dominio.puertos.EmisorDeTokens;
import com.chitgo.usuarios.dominio.puertos.PermisosPorRol;

/**
 * Valida el JWT y arma la autenticación de Spring Security. Lo usan el filtro de las requests HTTP y el
 * interceptor del CONNECT del WebSocket, así los dos validan igual.
 */
@Component
public class AutenticadorPorToken {

    private final EmisorDeTokens emisor;
    private final PermisosPorRol permisosPorRol;

    public AutenticadorPorToken(EmisorDeTokens emisor, PermisosPorRol permisosPorRol) {
        this.emisor = emisor;
        this.permisosPorRol = permisosPorRol;
    }

    /**
     * El principal es un {@link UsuarioAutenticado}. Las autoridades son ROLE_ más el código del rol (ROLE_MOZO)
     * y cada permiso del rol en rol_permiso (mesa.ver_asignadas), que es lo que piden los @PreAuthorize.
     *
     * @throws TokenInvalidoException si el token está vencido, alterado o mal formado
     */
    public Authentication autenticar(String token) {
        UsuarioAutenticado usuario = UsuarioAutenticado.desde(emisor.leer(token));
        List<SimpleGrantedAuthority> autoridades = new ArrayList<>();
        autoridades.add(new SimpleGrantedAuthority("ROLE_" + usuario.rol()));
        for (String permiso : permisosPorRol.de(usuario.rol())) {
            autoridades.add(new SimpleGrantedAuthority(permiso));
        }
        return UsernamePasswordAuthenticationToken.authenticated(usuario, null, autoridades);
    }
}
