package com.chitgo.usuarios.aplicacion.dto;

import java.time.LocalDate;

/** POST /admin/empleados/{id}/ausencias. El tipo llega como texto; {@code hasta} puede no venir. */
public record DatosDeAusencia(String tipo, LocalDate desde, LocalDate hasta) {
}
