package com.chitgo.usuarios.infraestructura.seguridad;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

import com.chitgo.usuarios.dominio.TokenInvalidoException;

/**
 * Lee el JWT de la cookie y, si es válido, deja al usuario autenticado para esta request. Si no hay cookie
 * o el token no sirve, la request sigue sin usuario: las rutas públicas responden y el resto da 401.
 * No se registra como bean para que el contenedor no lo corra además fuera de Spring Security.
 */
public class FiltroDeSesion extends OncePerRequestFilter {

    private final SecurityContextHolderStrategy contextos = SecurityContextHolder.getContextHolderStrategy();
    private final CookieDeSesion cookieDeSesion;
    private final AutenticadorPorToken autenticador;

    public FiltroDeSesion(CookieDeSesion cookieDeSesion, AutenticadorPorToken autenticador) {
        this.cookieDeSesion = cookieDeSesion;
        this.autenticador = autenticador;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        cookieDeSesion.leer(request).ifPresent(this::autenticar);
        chain.doFilter(request, response);
    }

    private void autenticar(String token) {
        try {
            SecurityContext contexto = contextos.createEmptyContext();
            contexto.setAuthentication(autenticador.autenticar(token));
            contextos.setContext(contexto);
        } catch (TokenInvalidoException e) {
            // Sin usuario: si la ruta pide sesión, el entry point responde 401.
        }
    }
}
