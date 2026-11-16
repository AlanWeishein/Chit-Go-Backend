package com.chitgo.usuarios.infraestructura.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import com.chitgo.compartido.aplicacion.ReglasDePrueba;

class CookieDeDispositivoTest {

    @Test
    void esHttpOnlyStrictViveSieteDiasYSoloViajaAAuth() {
        ResponseCookie cookie = new CookieDeDispositivo(new CookieDeSesion(true), ReglasDePrueba.DE_HOY)
                .crear("la.habilitacion.firmada");

        assertThat(cookie.getName()).isEqualTo(CookieDeDispositivo.NOMBRE);
        assertThat(cookie.getValue()).isEqualTo("la.habilitacion.firmada");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void enDesarrolloVaSinSecureComoLaDeSesion() {
        assertThat(new CookieDeDispositivo(new CookieDeSesion(false), ReglasDePrueba.DE_HOY).crear("x").isSecure()).isFalse();
    }
}
