package com.chitgo.usuarios.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/** El bloqueo por intentos fallidos (RNF-07), sin base. */
class UsuarioTest {

    private static final Instant AHORA = Instant.parse("2026-10-16T12:00:00Z");

    private final Usuario juan = new Usuario(1L, new Rol("MOZO", "Mozo"), "Juan", "juan", null, "hash", "hash");

    @Test
    void sinIntentosNoEstaBloqueado() {
        assertThat(juan.estaBloqueado(AHORA)).isFalse();
        assertThat(juan.getBloqueadoHasta()).isNull();
    }

    @Test
    void cuatroIntentosFallidosNoLoBloquean() {
        fallar(4);

        assertThat(juan.getIntentosFallidos()).isEqualTo((short) 4);
        assertThat(juan.estaBloqueado(AHORA)).isFalse();
    }

    @Test
    void alQuintoIntentoFallidoQuedaBloqueadoSinVencimiento() {
        fallar(5);

        assertThat(juan.getIntentosFallidos()).isEqualTo((short) 5);
        assertThat(juan.getBloqueadoHasta()).isEqualTo(Usuario.BLOQUEO_SIN_VENCIMIENTO);
        assertThat(juan.estaBloqueado(AHORA)).isTrue();
        // No vence solo: sigue bloqueado aunque pasen años.
        assertThat(juan.estaBloqueado(AHORA.plus(Duration.ofDays(365 * 10)))).isTrue();
    }

    @Test
    void entrarBienVuelveLosIntentosACero() {
        // CP-10-05
        fallar(2);

        juan.registrarIngreso();

        assertThat(juan.getIntentosFallidos()).isZero();
        assertThat(juan.estaBloqueado(AHORA)).isFalse();
    }

    @Test
    void elBloqueoEntraEnUnTimestampDeMysql() {
        assertThat(Usuario.BLOQUEO_SIN_VENCIMIENTO).isBefore(Instant.parse("2038-01-19T03:14:07Z"));
    }

    @Test
    void entranConPinSoloMozoCajaYCocina() {
        assertThat(new Rol("MOZO", "Mozo").entraConPin()).isTrue();
        assertThat(new Rol("CAJA", "Caja").entraConPin()).isTrue();
        assertThat(new Rol("COCINA", "Cocina").entraConPin()).isTrue();
        assertThat(new Rol("ADMIN", "Admin").entraConPin()).isFalse();
        assertThat(new Rol("GERENTE_DUENO", "Gerente/Dueño").entraConPin()).isFalse();
    }

    @Test
    void entranConEmailSoloElAdminYElGerenteDueno() {
        assertThat(new Rol("ADMIN", "Admin").entraConEmail()).isTrue();
        assertThat(new Rol("GERENTE_DUENO", "Gerente/Dueño").entraConEmail()).isTrue();
        assertThat(new Rol("MOZO", "Mozo").entraConEmail()).isFalse();
        assertThat(new Rol("CAJA", "Caja").entraConEmail()).isFalse();
        assertThat(new Rol("COCINA", "Cocina").entraConEmail()).isFalse();
    }

    private void fallar(int veces) {
        for (int i = 0; i < veces; i++) {
            juan.registrarIntentoFallido();
        }
    }
}
