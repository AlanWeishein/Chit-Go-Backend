package com.chitgo.usuarios.infraestructura.web;

import java.time.LocalTime;

import com.chitgo.usuarios.aplicacion.dto.DatosDeTurno;

/**
 * Un turno del cuerpo de PUT /admin/empleados/{id}/turnos, con las horas como "12:00". Lo que falta, el día y si
 * se pisa con otro lo controla el Service, con un mensaje.
 */
public record TurnoRequest(String dia, LocalTime horaInicio, LocalTime horaFin) {

    DatosDeTurno aDatos() {
        return new DatosDeTurno(dia, horaInicio, horaFin);
    }
}
