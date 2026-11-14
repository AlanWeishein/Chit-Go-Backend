package com.chitgo.usuarios.aplicacion.dto;

import java.util.List;

/**
 * GET /admin/empleados/{id}. Las mesas van como id: son de {@code pedidos}. Las ausencias, de la más nueva a la
 * más vieja. Nunca lleva un PIN, una contraseña ni su hash.
 */
public record EmpleadoDetalleDto(Long id, String nombre, String rol, String nombreUsuario, boolean activo,
        boolean bloqueado, boolean ausenciaVigente, boolean debeCambiarCredencial, boolean contrasenaProvisoria,
        List<TurnoDto> turnos, List<Long> mesaIds, List<AusenciaDto> ausencias) {
}
