package com.chitgo.usuarios.infraestructura.web;

import jakarta.validation.constraints.Pattern;

/**
 * El cuerpo de POST /admin/empleados/{id}/pin: el PIN provisorio que escribe el Admin, como en el alta. El formato
 * da el 400 genérico; que falte lo controla el Service, con "Falta el PIN".
 */
public record BlanqueoDePinRequest(@Pattern(regexp = "\\d{4,10}") String pin) {
}
