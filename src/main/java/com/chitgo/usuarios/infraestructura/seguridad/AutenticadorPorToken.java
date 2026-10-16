package com.chitgo.usuarios.infraestructura.seguridad;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import com.chitgo.usuarios.dominio.TokenInvalidoException;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;
import com.chitgo.usuarios.dominio.puertos.EmisorDeTokens;

/**
 * Valida el JWT y arma la autenticación de Spring Security. Lo usan el filtro de las requests HTTP y el
 * interceptor del CONNECT del WebSocket, así los dos validan igual.
 */
@Component
public class AutenticadorPorToken {

    private final EmisorDeTokens emisor;

    public AutenticadorPorToken(EmisorDeTokens emisor) {
        this.emisor = emisor;
    }

    /**
     * El principal es un {@link UsuarioAutenticado} y la autoridad, ROLE_ más el código del rol (ROLE_MOZO).
     *
     * @throws TokenInvalidoException si el token está vencido, alterado o mal formado
     */
    public Authentication autenticar(String token) {
        UsuarioAutenticado usuario = UsuarioAutenticado.desde(emisor.leer(token));
        return UsernamePasswordAuthenticationToken.authenticated(
                usuario, null, List.of(new SimpleGrantedAuthority("ROLE_" + usuario.rol())));
    }
}
