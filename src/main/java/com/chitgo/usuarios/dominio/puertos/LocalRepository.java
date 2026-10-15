package com.chitgo.usuarios.dominio.puertos;

import java.util.Optional;

import com.chitgo.usuarios.dominio.Local;

public interface LocalRepository {

    Optional<Local> buscarPorId(Long id);
}
