package com.chitgo.menu.infraestructura.web;

import jakarta.validation.constraints.NotNull;

/** El cuerpo de PATCH /platos/{id}/disponibilidad. */
public record DisponibilidadRequest(@NotNull Boolean disponible) {
}
