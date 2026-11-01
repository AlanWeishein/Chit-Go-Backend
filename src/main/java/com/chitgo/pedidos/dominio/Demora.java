package com.chitgo.pedidos.dominio;

import java.time.Instant;

/**
 * La regla de los 3 minutos (RF-16), en un solo lugar para que el GET de la cola de Caja y el aviso
 * ALERTA_DEMORA nunca digan cosas distintas (ST-10, 29/10).
 */
public final class Demora {

    /** Más de esto sin resolverse es demorado: a los 180 segundos todavía no, a los 181 sí (CP-20-01). */
    static final long SEGUNDOS_HASTA_DEMORA = 180;

    private Demora() {
    }

    /** @param segundosDeEspera los segundos enteros desde el envío, truncados */
    public static boolean estaDemorado(long segundosDeEspera) {
        return segundosDeEspera > SEGUNDOS_HASTA_DEMORA;
    }

    /**
     * El {@code enviadoEn} más nuevo que a esta hora ya está demorado. Como los segundos de espera se truncan,
     * "más de 180" es "pasaron 181 segundos enteros": un pedido está demorado si y solo si
     * {@code enviadoEn <= limiteDeEnvio(ahora)}.
     */
    public static Instant limiteDeEnvio(Instant ahora) {
        return ahora.minusSeconds(SEGUNDOS_HASTA_DEMORA + 1);
    }
}
