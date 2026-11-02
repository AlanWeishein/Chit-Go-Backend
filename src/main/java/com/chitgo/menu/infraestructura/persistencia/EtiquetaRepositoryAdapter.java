package com.chitgo.menu.infraestructura.persistencia;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.chitgo.menu.dominio.Etiqueta;
import com.chitgo.menu.dominio.puertos.EtiquetaRepository;

@Repository
class EtiquetaRepositoryAdapter implements EtiquetaRepository {

    private final EtiquetaJpaRepository jpa;

    EtiquetaRepositoryAdapter(EtiquetaJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<Etiqueta> buscarPorIds(Long localId, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jpa.findByLocalIdAndIdIn(localId, ids);
    }
}
