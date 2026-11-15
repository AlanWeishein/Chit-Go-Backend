package com.chitgo.pedidos.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class DemoraTest {

    private static final Instant LAS_20 = Instant.parse("2026-10-20T20:00:00Z");

    /** La de hoy, el valor por defecto de chitgo.reglas.demora-del-pedido. */
    private static final Duration TRES_MINUTOS = Duration.ofSeconds(180);

    @Test
    void CP_20_01_a_los_180_segundos_todavia_no_y_a_los_181_si() {
        assertFalse(Demora.estaDemorado(180, TRES_MINUTOS));
        assertTrue(Demora.estaDemorado(181, TRES_MINUTOS));
    }

    @Test
    void el_limite_de_envio_es_181_segundos_antes() {
        assertEquals(Instant.parse("2026-10-20T19:56:59Z"), Demora.limiteDeEnvio(LAS_20, TRES_MINUTOS));
    }

    /** El número sale de la propiedad: con otra demora, el borde se corre igual en los dos métodos. */
    @Test
    void con_un_minuto_de_demora_a_los_60_todavia_no_y_a_los_61_si() {
        Duration unMinuto = Duration.ofSeconds(60);

        assertFalse(Demora.estaDemorado(60, unMinuto));
        assertTrue(Demora.estaDemorado(61, unMinuto));
        assertEquals(Instant.parse("2026-10-20T19:58:59Z"), Demora.limiteDeEnvio(LAS_20, unMinuto));
    }

    /** Los dos métodos dicen lo mismo: el GET (segundos truncados) y el aviso (límite de envío). */
    @Test
    void el_limite_de_envio_coincide_con_los_segundos_de_espera_truncados() {
        Instant ahora = LAS_20.plusMillis(500);
        Instant enElLimite = Demora.limiteDeEnvio(ahora, TRES_MINUTOS);
        Instant unNanoDespues = enElLimite.plusNanos(1);

        assertTrue(Demora.estaDemorado(Duration.between(enElLimite, ahora).getSeconds(), TRES_MINUTOS));
        assertFalse(Demora.estaDemorado(Duration.between(unNanoDespues, ahora).getSeconds(), TRES_MINUTOS));
    }
}
