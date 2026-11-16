package com.chitgo.usuarios.infraestructura.web;

/** El cuerpo de PATCH /gerente/admins/{id}/activo. Si falta, el Service responde 400 "Falta activo". */
public record ActivoRequest(Boolean activo) {
}
