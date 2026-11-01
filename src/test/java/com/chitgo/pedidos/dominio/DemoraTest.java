package com.chitgo.pedidos.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class DemoraTest {

    private static final Instant LAS_20 = Instant.parse("2026-10-20T20:00:00Z");

    @Test
    void CP_20_01_a_los_180_segundos_todavia_no_y_a_los_181_si() {
        assertFalse(Demora.estaDemorado(180));
        assertTrue(Demora.estaDemorado(181));
    }

    @Test
    void el_limite_de_envio_es_181_segundos_antes() {
        assertEquals(Instant.parse("2026-10-20T19:56:59Z"), Demora.limiteDeEnvio(LAS_20));
    }

    /** Los dos métodos dicen lo mismo: el GET (segundos truncados) y el aviso (límite de envío). */
    @Test
    void el_limite_de_envio_coincide_con_los_segundos_de_espera_truncados() {
        Instant ahora = LAS_20.plusMillis(500);
        Instant enElLimite = Demora.limiteDeEnvio(ahora);
        Instant unNanoDespues = enElLimite.plusNanos(1);

        assertTrue(Demora.estaDemorado(Duration.between(enElLimite, ahora).getSeconds()));
        assertFalse(Demora.estaDemorado(Duration.between(unNanoDespues, ahora).getSeconds()));
    }
}
