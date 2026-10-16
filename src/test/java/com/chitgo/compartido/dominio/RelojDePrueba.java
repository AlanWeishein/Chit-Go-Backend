package com.chitgo.compartido.dominio;

import java.time.Duration;
import java.time.Instant;

/** Reloj que solo avanza cuando el test lo pide. */
public class RelojDePrueba implements Reloj {

    private Instant ahora;

    public RelojDePrueba(Instant inicio) {
        this.ahora = inicio;
    }

    @Override
    public Instant ahora() {
        return ahora;
    }

    public void avanzar(Duration tiempo) {
        ahora = ahora.plus(tiempo);
    }
}
