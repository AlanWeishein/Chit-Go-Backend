package com.chitgo.usuarios.dominio.puertos;

import java.util.Optional;

import com.chitgo.usuarios.dominio.Rol;

public interface RolRepository {

    /** Por el código: MOZO, CAJA, COCINA, ADMIN o GERENTE_DUENO. */
    Optional<Rol> buscarPorCodigo(String codigo);
}
