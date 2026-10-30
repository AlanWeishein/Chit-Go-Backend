package com.chitgo.usuarios.infraestructura.seguridad;

import java.io.IOException;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.chitgo.usuarios.aplicacion.RenovarSesionService;
import com.chitgo.usuarios.aplicacion.ResultadoDeIngreso;
import com.chitgo.usuarios.dominio.TokenInvalidoException;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

/**
 * Lee el JWT de la cookie y, si es válido, deja al usuario autenticado para esta request. Si no hay cookie
 * o el token no sirve, la request sigue sin usuario: las rutas públicas responden y el resto da 401.
 *
 * <p>Si la ruta renueva (casi todas las que piden sesión), deja una cookie nueva con el T contado desde ahora,
 * y el usuario de esta request sale de ese token nuevo. No se registra como bean para que el contenedor no lo
 * corra además fuera de Spring Security.
 */
public class FiltroDeSesion extends OncePerRequestFilter {

    private final SecurityContextHolderStrategy contextos = SecurityContextHolder.getContextHolderStrategy();
    private final CookieDeSesion cookieDeSesion;
    private final AutenticadorPorToken autenticador;
    private final RenovarSesionService renovacion;
    private final RequestMatcher rutasQueNoRenuevan;

    /** @param rutasQueNoRenuevan las públicas y las que escriben o leen la cookie ellas mismas (SecurityConfig) */
    public FiltroDeSesion(CookieDeSesion cookieDeSesion, AutenticadorPorToken autenticador,
            RenovarSesionService renovacion, RequestMatcher rutasQueNoRenuevan) {
        this.cookieDeSesion = cookieDeSesion;
        this.autenticador = autenticador;
        this.renovacion = renovacion;
        this.rutasQueNoRenuevan = rutasQueNoRenuevan;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        cookieDeSesion.leer(request).ifPresent(token -> autenticar(token, request, response));
        chain.doFilter(request, response);
    }

    private void autenticar(String token, HttpServletRequest request, HttpServletResponse response) {
        Authentication autenticacion;
        try {
            autenticacion = autenticador.autenticar(token);
        } catch (TokenInvalidoException e) {
            // Sin usuario: si la ruta pide sesión, el entry point responde 401.
            return;
        }

        if (!rutasQueNoRenuevan.matches(request)) {
            UsuarioAutenticado usuario = (UsuarioAutenticado) autenticacion.getPrincipal();
            Optional<ResultadoDeIngreso> renovada = renovacion.renovar(usuario.usuarioId());
            if (renovada.isEmpty()) {
                // Dado de baja: queda sin usuario y la ruta da 401, aunque el token todavía no venció.
                return;
            }
            String nuevo = renovada.get().token();
            response.addHeader(HttpHeaders.SET_COOKIE,
                    cookieDeSesion.crear(nuevo, renovada.get().duracion()).toString());
            autenticacion = autenticador.autenticar(nuevo);
        }

        SecurityContext contexto = contextos.createEmptyContext();
        contexto.setAuthentication(autenticacion);
        contextos.setContext(contexto);
    }
}
