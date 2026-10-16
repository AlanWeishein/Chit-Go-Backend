package com.chitgo.usuarios.aplicacion.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** El largo de la contraseña, en caracteres y en bytes, sin Spring. */
class ContrasenaTest {

    private final Contrasena.Validador validador = new Contrasena.Validador();

    @Test
    void deOchoASesentaYCuatroCaracteres() {
        assertThat(valida("a".repeat(7))).isFalse();
        assertThat(valida("a".repeat(8))).isTrue();
        assertThat(valida("a".repeat(64))).isTrue();
        assertThat(valida("a".repeat(65))).isFalse();
        assertThat(valida("Chitgo2026!")).isTrue();
    }

    @Test
    void noPasaDeLos72BytesQueUsaBcrypt() {
        // La ñ ocupa 2 bytes: 36 son 72 bytes y entran; 37 son 74, aunque sean menos de 64 caracteres.
        assertThat(valida("ñ".repeat(36))).isTrue();
        assertThat(valida("ñ".repeat(37))).isFalse();
    }

    @Test
    void unEmojiCuentaComoUnCaracter() {
        // Cada uno son 2 char de Java y 4 bytes: 8 emojis son 8 caracteres y 32 bytes.
        assertThat(valida("🍕".repeat(8))).isTrue();
        assertThat(valida("🍕".repeat(7))).isFalse();
        // 19 son 76 bytes.
        assertThat(valida("🍕".repeat(19))).isFalse();
    }

    @Test
    void sinContrasenaLaControlaNotNull() {
        assertThat(valida(null)).isTrue();
    }

    private boolean valida(String clave) {
        return validador.isValid(clave, null);
    }
}
