package com.chitgo.menu.infraestructura.persistencia;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.chitgo.menu.dominio.Plato;
import com.chitgo.menu.dominio.puertos.CartaRepository;

@Repository
class CartaRepositoryAdapter implements CartaRepository {

    private final PlatoJpaRepository jpa;

    CartaRepositoryAdapter(PlatoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<Plato> listarActivosDelLocal(Long localId) {
        return jpa.activosConEtiquetas(localId);
    }

    @Override
    public List<Plato> buscarPorIds(Long localId, Collection<Long> ids) {
        return jpa.findByLocalIdAndIdIn(localId, ids);
    }
}
