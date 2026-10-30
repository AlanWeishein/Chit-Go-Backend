package com.chitgo.usuarios.infraestructura.seguridad;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * La cookie con el JWT: HttpOnly para que un script no la lea, SameSite=Strict y Path=/ para que también
 * viaje en el handshake de /ws. Secure se apaga solo en desarrollo, para probar desde el celular por la IP.
 */
@Component
public class CookieDeSesion {

    public static final String NOMBRE = "chitgo_sesion";

    private final boolean segura;

    public CookieDeSesion(@Value("${chitgo.sesion.cookie-segura:true}") boolean segura) {
        this.segura = segura;
    }

    public boolean esSegura() {
        return segura;
    }

    /** La cookie vive lo mismo que el token. */
    public ResponseCookie crear(String token, Duration duracion) {
        return base(token).maxAge(duracion).build();
    }

    /** Vacía y vencida, para que el navegador la borre. */
    public ResponseCookie borrar() {
        return base("").maxAge(Duration.ZERO).build();
    }

    public Optional<String> leer(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(c -> NOMBRE.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(valor -> !valor.isBlank())
                .findFirst();
    }

    private ResponseCookie.ResponseCookieBuilder base(String valor) {
        return ResponseCookie.from(NOMBRE, valor)
                .httpOnly(true)
                .secure(segura)
                .sameSite("Strict")
                .path("/");
    }
}
