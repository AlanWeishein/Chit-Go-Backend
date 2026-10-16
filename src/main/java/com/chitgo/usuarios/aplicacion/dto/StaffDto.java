package com.chitgo.usuarios.aplicacion.dto;

import java.util.List;

/**
 * Respuesta de GET /public/staff/{tokenStaff}: la lista del login con PIN. Es pública, así que lleva solo el
 * nombre del local y el id y el nombre de cada usuario.
 */
public record StaffDto(String nombreLocal, List<UsuarioDeStaffDto> usuarios) {

    public record UsuarioDeStaffDto(Long id, String nombre) {
    }
}
