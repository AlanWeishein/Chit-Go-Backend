package com.chitgo.usuarios.aplicacion;

import java.time.LocalDate;

import com.chitgo.compartido.aplicacion.ReglasDelNegocio;
import com.chitgo.compartido.dominio.Reloj;

/**
 * Las ausencias son días del local, así que "hoy" se cuenta en la zona del local ({@code chitgo.reglas.zona-horaria},
 * Argentina por defecto) y no en la de la conexión, que va en UTC (de 21 a 24 ya sería el día siguiente). Es la misma
 * zona que usa MozoDeTurnoAdapter.
 */
final class DiaDelLocal {

    private DiaDelLocal() {
    }

    static LocalDate hoy(Reloj reloj, ReglasDelNegocio reglas) {
        return reloj.ahora().atZone(reglas.zonaHoraria()).toLocalDate();
    }
}
