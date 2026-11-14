package com.chitgo.usuarios.aplicacion;

import java.time.LocalDate;
import java.time.ZoneId;

import com.chitgo.compartido.dominio.Reloj;

/**
 * Las ausencias son días del local, así que "hoy" se cuenta en la hora de Argentina y no en la de la conexión, que
 * va en UTC (de 21 a 24 ya sería el día siguiente). Es la misma zona que usa MozoDeTurnoAdapter.
 */
final class DiaDelLocal {

    static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    private DiaDelLocal() {
    }

    static LocalDate hoy(Reloj reloj) {
        return reloj.ahora().atZone(ZONA).toLocalDate();
    }
}
