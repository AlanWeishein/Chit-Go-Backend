package com.chitgo.usuarios.aplicacion.dto;

import java.time.LocalDate;

import com.chitgo.usuarios.dominio.TipoAusencia;

/** {@code hasta} es NULL si no tiene fecha de vuelta. */
public record AusenciaDto(Long id, TipoAusencia tipo, LocalDate desde, LocalDate hasta) {
}
