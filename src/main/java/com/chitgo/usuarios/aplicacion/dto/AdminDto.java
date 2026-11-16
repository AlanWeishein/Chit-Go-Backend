package com.chitgo.usuarios.aplicacion.dto;

/**
 * Un Admin en P-19 (HU-16): lo que lista y responden las rutas de /gerente/admins. El id es el del usuario. Nunca
 * trae la contraseña ni su hash.
 */
public record AdminDto(Long id, String nombre, String email, boolean activo, boolean bloqueado) {
}
