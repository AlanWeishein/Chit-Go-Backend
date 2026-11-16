package com.chitgo.menu.infraestructura.web;

import jakarta.validation.constraints.NotNull;

/** El cuerpo de PATCH /admin/platos/{id}/destacado. */
public record DestacarPlatoRequest(@NotNull Boolean destacado) {
}
