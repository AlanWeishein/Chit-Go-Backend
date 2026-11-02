package com.chitgo.menu.infraestructura.persistencia;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chitgo.menu.dominio.Etiqueta;

interface EtiquetaJpaRepository extends JpaRepository<Etiqueta, Long> {

    List<Etiqueta> findByLocalIdAndIdIn(Long localId, Collection<Long> ids);

    Optional<Etiqueta> findByIdAndLocalId(Long id, Long localId);

    /** El ORDER BY del nombre usa el collation de la base (ai_ci). */
    List<Etiqueta> findByLocalIdOrderByNombreAscIdAsc(Long localId);

    /** La comparación la hace MySQL con el collation de la columna (ai_ci). */
    boolean existsByLocalIdAndNombre(Long localId, String nombre);

    boolean existsByLocalIdAndNombreAndIdNot(Long localId, String nombre, Long id);
}
