package com.chitgo.menu.infraestructura.persistencia;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.chitgo.menu.dominio.CategoriaMenu;
import com.chitgo.menu.dominio.puertos.CategoriaMenuRepository;

@Repository
class CategoriaMenuRepositoryAdapter implements CategoriaMenuRepository {

    private final CategoriaMenuJpaRepository jpa;

    CategoriaMenuRepositoryAdapter(CategoriaMenuJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<CategoriaMenu> buscarPorId(Long id, Long localId) {
        return jpa.findByIdAndLocalId(id, localId);
    }
}
