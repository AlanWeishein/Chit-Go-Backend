package com.chitgo.menu.dominio.puertos;

/**
 * El aviso a los celulares de HU-09 cuando un plato se apaga o se vuelve a prender. Lo declara menu y lo implementa
 * pedidos, que es quien conoce las sesiones de las mesas (decidido el 29/10). Va a la cola de cada sesión abierta del
 * local, no a un topic nuevo: así no cambia el interceptor de suscripciones.
 */
public interface AvisoDeDisponibilidad {

    /** Sale después del commit, si hay una transacción en curso: nadie se entera de un cambio que se deshace. */
    void platoCambio(Long localId, Long platoId, boolean disponible);
}
