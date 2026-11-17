package com.chitgo.usuarios.dominio;

/**
 * Lo que viaja en el token de sesión.
 *
 * @param rol      el código del rol, incluido SUPERADMIN en el prototipo experimental
 * @param localId  NULL para el Gerente/Dueño, que no tiene local fijo
 */
public record DatosDeSesion(Long usuarioId, String rol, Long localId, boolean debeCambiarCredencial) {
}
