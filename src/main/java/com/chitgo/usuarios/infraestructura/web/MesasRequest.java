package com.chitgo.usuarios.infraestructura.web;

import java.util.List;

/** El cuerpo de PUT /admin/empleados/{id}/mesas. */
public record MesasRequest(List<Long> mesaIds) {
}
