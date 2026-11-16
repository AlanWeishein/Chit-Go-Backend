package com.chitgo.usuarios.infraestructura.web;

import java.time.LocalDate;

import com.chitgo.usuarios.aplicacion.dto.DatosDeAusencia;

/** El cuerpo de POST /admin/empleados/{id}/ausencias, con las fechas como "2026-11-14". {@code hasta} es opcional. */
public record AusenciaRequest(String tipo, LocalDate desde, LocalDate hasta) {

    DatosDeAusencia aDatos() {
        return new DatosDeAusencia(tipo, desde, hasta);
    }
}
