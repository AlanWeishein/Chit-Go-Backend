package com.chitgo.usuarios.aplicacion.dto;

import java.time.LocalTime;

/** Un turno de PUT /admin/empleados/{id}/turnos. El día llega como texto (LUN a DOM). */
public record DatosDeTurno(String dia, LocalTime horaInicio, LocalTime horaFin) {
}
