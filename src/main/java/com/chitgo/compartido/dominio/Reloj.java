package com.chitgo.compartido.dominio;

import java.time.Instant;

/** La hora actual. Es un puerto para que los tests puedan mover el tiempo sin esperar. */
public interface Reloj {

    Instant ahora();
}
