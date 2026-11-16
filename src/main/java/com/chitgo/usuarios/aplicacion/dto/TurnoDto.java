package com.chitgo.usuarios.aplicacion.dto;

import java.time.LocalTime;

import com.chitgo.usuarios.dominio.DiaSemana;

/** Un turno de la plantilla semanal. Si {@code horaFin} es menor que {@code horaInicio}, termina al día siguiente. */
public record TurnoDto(DiaSemana dia, LocalTime horaInicio, LocalTime horaFin) {
}
