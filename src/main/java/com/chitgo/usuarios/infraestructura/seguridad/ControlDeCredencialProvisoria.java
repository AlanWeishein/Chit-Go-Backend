package com.chitgo.usuarios.infraestructura.seguridad;

import java.util.function.Supplier;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import com.chitgo.usuarios.dominio.CredencialProvisoriaException;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

/**
 * Pide sesión y que la credencial no sea provisoria (CU-12, 1a). Con la provisoria, toda ruta autenticada da
 * 403, menos las que SecurityConfig deja aparte: /auth/credencial, /auth/sesion y /auth/logout. Mira el
 * debeCambiarCredencial del token, sin consultar la base, como el resto del filtro.
 */
public class ControlDeCredencialProvisoria implements AuthorizationManager<RequestAuthorizationContext> {

    /** La negativa por la credencial provisoria, para distinguirla de otros 403 al armar la respuesta. */
    private static final AuthorizationDecision PROVISORIA = new AuthorizationDecision(false);

    private static final AuthorizationDecision SIN_SESION = new AuthorizationDecision(false);
    private static final AuthorizationDecision CONCEDIDA = new AuthorizationDecision(true);

    /** Sin sesión también niega: como el usuario es anónimo, Spring Security lo responde con el 401. */
    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> autenticacion,
            RequestAuthorizationContext contexto) {
        Authentication actual = autenticacion.get();
        if (actual == null || !(actual.getPrincipal() instanceof UsuarioAutenticado usuario)) {
            return SIN_SESION;
        }
        return usuario.debeCambiarCredencial() ? PROVISORIA : CONCEDIDA;
    }

    /** El 403 que va a la respuesta: el del primer ingreso, con su código, si se negó por la credencial. */
    public Exception traducir(AccessDeniedException e) {
        if (e instanceof AuthorizationDeniedException negada && negada.getAuthorizationResult() == PROVISORIA) {
            return new CredencialProvisoriaException();
        }
        return e;
    }
}
