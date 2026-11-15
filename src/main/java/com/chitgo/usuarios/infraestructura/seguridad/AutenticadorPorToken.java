package com.chitgo.usuarios.infraestructura.seguridad;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import com.chitgo.usuarios.dominio.TokenInvalidoException;
import com.chitgo.usuarios.dominio.TokenLeido;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;
import com.chitgo.usuarios.dominio.puertos.EmisorDeTokens;
import com.chitgo.usuarios.dominio.puertos.PermisosPorRol;
import com.chitgo.usuarios.dominio.puertos.UsuarioRepository;

/**
 * Valida el JWT y arma la autenticación de Spring Security. Lo usan el filtro de las requests HTTP y el
 * interceptor del CONNECT del WebSocket, así los dos validan igual.
 */
@Component
public class AutenticadorPorToken {

    private final EmisorDeTokens emisor;
    private final PermisosPorRol permisosPorRol;
    private final UsuarioRepository usuarios;

    public AutenticadorPorToken(EmisorDeTokens emisor, PermisosPorRol permisosPorRol, UsuarioRepository usuarios) {
        this.emisor = emisor;
        this.permisosPorRol = permisosPorRol;
        this.usuarios = usuarios;
    }

    /**
     * Valida el token y que no sea de antes del último logout del usuario (CP-13-01). Es una consulta a la base.
     * El principal es un {@link UsuarioAutenticado}. Las autoridades son ROLE_ más el código del rol (ROLE_MOZO)
     * y cada permiso del rol en rol_permiso (mesa.ver_asignadas), que es lo que piden los @PreAuthorize.
     *
     * @throws TokenInvalidoException si el token está vencido, alterado o mal formado, si es de antes del logout,
     *                                o si el usuario ya no existe
     */
    public Authentication autenticar(String token) {
        TokenLeido leido = emisor.leer(token);
        boolean vale = usuarios.buscarPorId(leido.datos().usuarioId())
                .map(usuario -> usuario.aceptaSesionEmitidaEn(leido.emitidoEn()))
                .orElse(false);
        if (!vale) {
            throw new TokenInvalidoException("El token es de antes del cierre de sesión");
        }
        return armar(leido);
    }

    /**
     * Solo la firma y el vencimiento, sin ir a la base. Lo usa FiltroDeSesion en las rutas que renuevan, donde
     * el "válido desde" lo compara RenovarSesionService con el usuario que ya carga.
     *
     * @throws TokenInvalidoException si el token está vencido, alterado o mal formado
     */
    public TokenLeido leer(String token) {
        return emisor.leer(token);
    }

    /**
     * Para el token que el back acaba de emitir al renovar: nace después del "válido desde", así que no se
     * vuelve a mirar la base.
     */
    public Authentication autenticarRecienEmitido(String token) {
        return armar(emisor.leer(token));
    }

    private Authentication armar(TokenLeido leido) {
        UsuarioAutenticado usuario = UsuarioAutenticado.desde(leido);
        List<SimpleGrantedAuthority> autoridades = new ArrayList<>();
        autoridades.add(new SimpleGrantedAuthority("ROLE_" + usuario.rol()));
        for (String permiso : permisosPorRol.de(usuario.rol())) {
            autoridades.add(new SimpleGrantedAuthority(permiso));
        }
        return UsernamePasswordAuthenticationToken.authenticated(usuario, null, autoridades);
    }
}
