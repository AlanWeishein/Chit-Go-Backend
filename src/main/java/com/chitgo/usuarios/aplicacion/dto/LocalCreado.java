package com.chitgo.usuarios.aplicacion.dto;

/** Lo que muestra el script al dar de alta un local: el token del QR de staff y el Gerente/Dueño que lo tiene. */
public record LocalCreado(Long localId, String nombreLocal, String tokenStaff, Long gerenteId, String email) {
}
