package com.chitgo.usuarios.dominio.puertos;

import java.util.Optional;

import com.chitgo.usuarios.dominio.Rol;

public interface RolRepository {

    /** Por el código de los datos fijos de roles, incluido SUPERADMIN experimental. */
    Optional<Rol> buscarPorCodigo(String codigo);
}
