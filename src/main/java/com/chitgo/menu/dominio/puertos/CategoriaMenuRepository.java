package com.chitgo.menu.dominio.puertos;

import java.util.List;
import java.util.Optional;

import com.chitgo.menu.dominio.CategoriaMenu;

public interface CategoriaMenuRepository {

    CategoriaMenu guardar(CategoriaMenu categoria);

    /** Vacío si no existe o es de otro local (RNF-10). */
    Optional<CategoriaMenu> buscarPorId(Long id, Long localId);

    /** Las del local por orden, y por id si dos tienen el mismo. */
    List<CategoriaMenu> listarDelLocal(Long localId);

    /** Si tiene platos (también fuera de carta), lo frena la FK y tira CategoriaConPlatosException. */
    void borrar(CategoriaMenu categoria);
}
