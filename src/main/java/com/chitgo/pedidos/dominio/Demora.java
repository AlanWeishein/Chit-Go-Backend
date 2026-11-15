package com.chitgo.pedidos.dominio;

import java.time.Duration;
import java.time.Instant;

/**
 * La regla de los 3 minutos (RF-16), en un solo lugar para que el GET de la cola de Caja y el aviso
 * ALERTA_DEMORA nunca digan cosas distintas (ST-10, 29/10). El tiempo sale de {@code chitgo.reglas.demora-del-pedido}
 * (180 segundos por defecto) y lo pasan los dos services, que leen la misma propiedad.
 */
public final class Demora {

    private Demora() {
    }

    /**
     * Más de la demora sin resolverse es demorado: con 180 segundos, a los 180 todavía no y a los 181 sí (CP-20-01).
     *
     * @param segundosDeEspera los segundos enteros desde el envío, truncados
     * @param demora en segundos enteros
     */
    public static boolean estaDemorado(long segundosDeEspera, Duration demora) {
        return segundosDeEspera > demora.toSeconds();
    }

    /**
     * El {@code enviadoEn} más nuevo que a esta hora ya está demorado. Como los segundos de espera se truncan,
     * "más de 180" es "pasaron 181 segundos enteros": un pedido está demorado si y solo si
     * {@code enviadoEn <= limiteDeEnvio(ahora, demora)}.
     */
    public static Instant limiteDeEnvio(Instant ahora, Duration demora) {
        return ahora.minusSeconds(demora.toSeconds() + 1);
    }
}
