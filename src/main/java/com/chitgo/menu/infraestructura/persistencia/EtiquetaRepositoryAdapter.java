package com.chitgo.menu.infraestructura.persistencia;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import com.chitgo.menu.dominio.Etiqueta;
import com.chitgo.menu.dominio.EtiquetaRepetidaException;
import com.chitgo.menu.dominio.puertos.EtiquetaRepository;

@Repository
class EtiquetaRepositoryAdapter implements EtiquetaRepository {

    private final EtiquetaJpaRepository jpa;

    EtiquetaRepositoryAdapter(EtiquetaJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Etiqueta guardar(Etiqueta etiqueta) {
        try {
            // saveAndFlush: el INSERT o el UPDATE salen ya, así el UNIQUE salta acá y no al cerrar la transacción.
            return jpa.saveAndFlush(etiqueta);
        } catch (DataIntegrityViolationException e) {
            // Dos Admin crearon la misma a la vez: el Service ya había buscado. Otra violación es un error de verdad.
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains("uq_etiqueta_nombre")) {
                throw new EtiquetaRepetidaException();
            }
            throw e;
        }
    }

    @Override
    public Optional<Etiqueta> buscarPorId(Long id, Long localId) {
        return jpa.findByIdAndLocalId(id, localId);
    }

    @Override
    public List<Etiqueta> buscarPorIds(Long localId, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jpa.findByLocalIdAndIdIn(localId, ids);
    }

    @Override
    public List<Etiqueta> listarDelLocal(Long localId) {
        return jpa.findByLocalIdOrderByNombreAscIdAsc(localId);
    }

    @Override
    public boolean existeConNombre(Long localId, String nombre, Long salvoId) {
        if (salvoId == null) {
            return jpa.existsByLocalIdAndNombre(localId, nombre);
        }
        return jpa.existsByLocalIdAndNombreAndIdNot(localId, nombre, salvoId);
    }

    @Override
    public void borrar(Etiqueta etiqueta) {
        jpa.delete(etiqueta);
    }
}
