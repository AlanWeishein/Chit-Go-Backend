package com.chitgo.menu.infraestructura.persistencia;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.chitgo.menu.dominio.Plato;
import com.chitgo.menu.dominio.PlatosDeCategoria;
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

    @Override
    public Map<Long, PlatosDeCategoria> contarPorCategoria(Long localId) {
        Map<Long, PlatosDeCategoria> conteo = new HashMap<>();
        for (Object[] fila : jpa.contarPorCategoria(localId)) {
            conteo.put((Long) fila[0],
                    new PlatosDeCategoria(((Number) fila[1]).longValue(), ((Number) fila[2]).longValue()));
        }
        return conteo;
    }
}
