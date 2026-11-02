package com.chitgo.menu.dominio.puertos;

import java.util.Optional;

import com.chitgo.menu.dominio.CategoriaMenu;

public interface CategoriaMenuRepository {

    /** Vacío si no existe o es de otro local (RNF-10). */
    Optional<CategoriaMenu> buscarPorId(Long id, Long localId);
}
