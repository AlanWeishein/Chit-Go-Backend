package com.chitgo.usuarios.infraestructura.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.chitgo.compartido.dominio.RelojDePrueba;
import com.chitgo.usuarios.dominio.DatosDeSesion;
import com.chitgo.usuarios.dominio.DispositivoHabilitado;
import com.chitgo.usuarios.dominio.TokenInvalidoException;

class EmisorDeHabilitacionesJwtTest {

    private static final String CLAVE = Base64.getEncoder().encodeToString(
            "clave-de-prueba-de-32-bytes-o-mas!!".getBytes(StandardCharsets.UTF_8));
    private static final String OTRA_CLAVE = Base64.getEncoder().encodeToString(
            "otra-clave-de-prueba-de-32-bytes-o-mas".getBytes(StandardCharsets.UTF_8));
    private static final Instant INICIO = Instant.parse("2026-10-17T20:00:00Z");
    private static final Duration SIETE_DIAS = Duration.ofDays(7);

    private final RelojDePrueba reloj = new RelojDePrueba(INICIO);
    private final EmisorDeHabilitacionesJwt emisor = new EmisorDeHabilitacionesJwt(CLAVE, reloj);

    @Test
    void diceQueMozoEntroConContrasenaYCuando() {
        DispositivoHabilitado habilitado = emisor.leer(emisor.emitir(3L));

        assertThat(habilitado.usuarioId()).isEqualTo(3L);
        assertThat(habilitado.habilitadoEn()).isEqualTo(INICIO);
        assertThat(habilitado.venceEn()).isEqualTo(INICIO.plus(SIETE_DIAS));
    }

    @Test
    void sigueValiendoUnSegundoAntesDeLosSieteDias() {
        String token = emisor.emitir(3L);

        reloj.avanzar(SIETE_DIAS.minusSeconds(1));

        assertThat(emisor.leer(token).usuarioId()).isEqualTo(3L);
    }

    @Test
    void venceUnSegundoDespuesDeLosSieteDias() {
        String token = emisor.emitir(3L);

        reloj.avanzar(SIETE_DIAS.plusSeconds(1));

        assertThatThrownBy(() -> emisor.leer(token)).isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void unTokenDeSesionNoSirveComoHabilitacion() {
        // Misma clave: lo único que los separa es el audience.
        String sesion = new EmisorDeTokensJwt(CLAVE, reloj)
                .emitir(new DatosDeSesion(3L, "MOZO", 1L, false), Duration.ofMinutes(10));

        assertThatThrownBy(() -> emisor.leer(sesion)).isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void unaHabilitacionNoSirveComoTokenDeSesion() {
        String habilitacion = emisor.emitir(3L);

        assertThatThrownBy(() -> new EmisorDeTokensJwt(CLAVE, reloj).leer(habilitacion))
                .isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void rechazaUnaHabilitacionDeOtroMozoConElSubjectCambiado() {
        String[] partes = emisor.emitir(3L).split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8);
        String payloadDeOtro = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.replace("\"sub\":\"3\"", "\"sub\":\"4\"").getBytes(StandardCharsets.UTF_8));
        assertThat(payloadDeOtro).isNotEqualTo(partes[1]);

        String alterado = partes[0] + "." + payloadDeOtro + "." + partes[2];

        assertThatThrownBy(() -> emisor.leer(alterado)).isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void rechazaUnaHabilitacionFirmadaConOtraClave() {
        String deOtraClave = new EmisorDeHabilitacionesJwt(OTRA_CLAVE, reloj).emitir(3L);

        assertThatThrownBy(() -> emisor.leer(deOtraClave)).isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void rechazaUnTextoQueNoEsUnToken() {
        assertThatThrownBy(() -> emisor.leer("cualquier-cosa")).isInstanceOf(TokenInvalidoException.class);
    }
}
