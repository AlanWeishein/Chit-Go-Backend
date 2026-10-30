package com.chitgo.usuarios.infraestructura.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;

class CookieDeSesionTest {

    private final CookieDeSesion segura = new CookieDeSesion(true);

    @Test
    void laCookieDelTokenEsHttpOnlyStrictYViveLoQueElToken() {
        ResponseCookie cookie = segura.crear("el.jwt.firmado", Duration.ofMinutes(10));

        assertThat(cookie.getName()).isEqualTo(CookieDeSesion.NOMBRE);
        assertThat(cookie.getValue()).isEqualTo("el.jwt.firmado");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void enDesarrolloVaSinSecure() {
        assertThat(new CookieDeSesion(false).crear("token", Duration.ofMinutes(10)).isSecure()).isFalse();
    }

    @Test
    void borrarlaLaDejaVaciaYVencida() {
        ResponseCookie cookie = segura.borrar();

        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/");
    }

    @Test
    void leeElTokenSoloDeSuCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(segura.leer(request)).isEmpty();

        request.setCookies(new Cookie("otra", "x"), new Cookie(CookieDeSesion.NOMBRE, "el.jwt.firmado"));
        assertThat(segura.leer(request)).contains("el.jwt.firmado");

        request.setCookies(new Cookie(CookieDeSesion.NOMBRE, ""));
        assertThat(segura.leer(request)).isEmpty();
    }
}
