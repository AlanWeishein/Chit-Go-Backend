package com.chitgo.menu.infraestructura.persistencia;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import com.chitgo.menu.dominio.CategoriaConPlatosException;
import com.chitgo.menu.dominio.CategoriaMenu;
import com.chitgo.menu.dominio.puertos.CategoriaMenuRepository;

@Repository
class CategoriaMenuRepositoryAdapter implements CategoriaMenuRepository {

    private final CategoriaMenuJpaRepository jpa;

    CategoriaMenuRepositoryAdapter(CategoriaMenuJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public CategoriaMenu guardar(CategoriaMenu categoria) {
        return jpa.save(categoria);
    }

    @Override
    public Optional<CategoriaMenu> buscarPorId(Long id, Long localId) {
        return jpa.findByIdAndLocalId(id, localId);
    }

    @Override
    public List<CategoriaMenu> listarDelLocal(Long localId) {
        return jpa.findByLocalIdOrderByOrdenAscIdAsc(localId);
    }

    @Override
    public void borrar(CategoriaMenu categoria) {
        try {
            // flush: el DELETE sale ya, así la FK salta acá y no al cerrar la transacción.
            jpa.delete(categoria);
            jpa.flush();
        } catch (DataIntegrityViolationException e) {
            // El Service ya contó los platos: esto es uno que entró justo antes. Otra violación es un error de verdad.
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains("fk_plato_categoria")) {
                throw new CategoriaConPlatosException();
            }
            throw e;
        }
    }
}
