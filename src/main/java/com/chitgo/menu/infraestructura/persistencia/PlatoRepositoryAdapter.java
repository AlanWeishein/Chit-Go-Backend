package com.chitgo.menu.infraestructura.persistencia;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.chitgo.menu.dominio.Plato;
import com.chitgo.menu.dominio.puertos.PlatoRepository;

@Repository
class PlatoRepositoryAdapter implements PlatoRepository {

    private final PlatoJpaRepository jpa;

    PlatoRepositoryAdapter(PlatoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Plato guardar(Plato plato) {
        return jpa.save(plato);
    }

    @Override
    public Optional<Plato> buscarPorId(Long id, Long localId) {
        return jpa.findByIdAndLocalId(id, localId);
    }

    @Override
    public List<Plato> listarDelLocal(Long localId) {
        return jpa.todosConEtiquetas(localId);
    }
}
