package com.chitgo.usuarios.aplicacion.dto;

import java.util.List;

/**
 * Una fila de GET /admin/empleados. {@code rol} es el código (MOZO, CAJA o COCINA) y {@code nombreUsuario}, NULL
 * si no es mozo. Los dados de baja también salen, con {@code activo} en false.
 */
public record EmpleadoResumenDto(Long id, String nombre, String rol, String nombreUsuario, boolean activo,
        boolean bloqueado, boolean ausenciaVigente, List<TurnoDto> turnos) {
}
