package com.chitgo.usuarios.infraestructura.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CodificadorDeClavesBCryptTest {

    private final CodificadorDeClavesBCrypt codificador = new CodificadorDeClavesBCrypt();

    @Test
    void hasheaYVerificaLaMismaClave() {
        String hash = codificador.hashear("1234");

        assertThat(hash).startsWith("$2a$10$").doesNotContain("1234");
        assertThat(codificador.verificar("1234", hash)).isTrue();
    }

    @Test
    void rechazaUnaClaveDistinta() {
        String hash = codificador.hashear("Chitgo2026!");

        assertThat(codificador.verificar("chitgo2026!", hash)).isFalse();
        assertThat(codificador.verificar("", hash)).isFalse();
    }

    @Test
    void laMismaClaveDaHashesDistintosPorLaSal() {
        assertThat(codificador.hashear("1234")).isNotEqualTo(codificador.hashear("1234"));
    }

    @Test
    void sinHashNoVerificaNada() {
        // Un Admin no tiene pin_hash, y Caja y Cocina no tienen hash_password.
        assertThat(codificador.verificar("1234", null)).isFalse();
        assertThat(codificador.verificar("1234", "")).isFalse();
        assertThat(codificador.verificar(null, codificador.hashear("1234"))).isFalse();
    }

    @Test
    void verificaLosHashesDeLosDatosDePrueba() {
        // PIN de Juan (La Esquina) y contraseña del Dueño de La Esquina, copiados de R__datos_de_prueba.sql.
        assertThat(codificador.verificar("1234", "$2a$10$t2Q1Q8gud.hYJMxAsBRp4ek5rx8/sWGJ.6C6ft1i8lFprl.zN5HnW")).isTrue();
        assertThat(codificador.verificar("Chitgo2026!", "$2a$10$kB05zmuM6yxVybIiDOKGrOsFHFdpI1QGAGqKFnChLWH8kIBeDFMne")).isTrue();
    }
}
