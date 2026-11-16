package com.chitgo.usuarios.infraestructura.web;

import java.time.LocalDate;

/** El cuerpo de PATCH /admin/empleados/{id}/ausencias/{ausenciaId}: el último día de la ausencia. */
public record FinDeAusenciaRequest(LocalDate hasta) {
}
