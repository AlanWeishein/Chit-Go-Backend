package com.chitgo.usuarios.infraestructura.seguridad;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.chitgo.usuarios.dominio.DispositivoHabilitado;

/**
 * La cookie que dice que un mozo entró con usuario y contraseña en este dispositivo, aparte de la de sesión
 * (decidido el 09/10). HttpOnly y SameSite=Strict como la de sesión, y con su mismo Secure. Path=/api/v1/auth:
 * viaja a todo lo que cuelga de esa ruta (el PIN, el login del mozo, /auth/dispositivo) y a nada más. Una
 * sola por navegador: si otro mozo entra con contraseña en el mismo celular, la pisa.
 */
@Component
public class CookieDeDispositivo {

    public static final String NOMBRE = "chitgo_dispositivo";
    static final String RUTA = "/api/v1/auth";

    private final CookieDeSesion cookieDeSesion;

    public CookieDeDispositivo(CookieDeSesion cookieDeSesion) {
        this.cookieDeSesion = cookieDeSesion;
    }

    /** Vive lo mismo que la habilitación: 7 días. El vencimiento que vale es el del token. */
    public ResponseCookie crear(String token) {
        return ResponseCookie.from(NOMBRE, token)
                .httpOnly(true)
                .secure(cookieDeSesion.esSegura())
                .sameSite("Strict")
                .path(RUTA)
                .maxAge(DispositivoHabilitado.VIGENCIA)
                .build();
    }
}
