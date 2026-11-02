package com.chitgo.menu.infraestructura.persistencia;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chitgo.menu.dominio.Plato;

interface PlatoJpaRepository extends JpaRepository<Plato, Long> {

    @Query("SELECT DISTINCT p FROM Plato p JOIN FETCH p.categoria "
            + "LEFT JOIN FETCH p.platoEtiquetas pe LEFT JOIN FETCH pe.etiqueta "
            + "WHERE p.localId = :localId AND p.activo = TRUE")
    List<Plato> activosConEtiquetas(@Param("localId") Long localId);

    List<Plato> findByLocalIdAndIdIn(Long localId, Collection<Long> ids);

    /** También los fuera de carta. El ORDER BY del nombre usa el collation de la base (ai_ci), como la carta. */
    @Query("SELECT DISTINCT p FROM Plato p JOIN FETCH p.categoria c "
            + "LEFT JOIN FETCH p.platoEtiquetas pe LEFT JOIN FETCH pe.etiqueta "
            + "WHERE p.localId = :localId ORDER BY c.orden, c.id, p.nombre, p.id")
    List<Plato> todosConEtiquetas(@Param("localId") Long localId);

    Optional<Plato> findByIdAndLocalId(Long id, Long localId);

    /** Por cada categoría con platos: su id, cuántos tiene y cuántos están fuera de carta. */
    @Query("SELECT p.categoria.id, COUNT(p), SUM(CASE WHEN p.activo = FALSE THEN 1 ELSE 0 END) FROM Plato p "
            + "WHERE p.localId = :localId GROUP BY p.categoria.id")
    List<Object[]> contarPorCategoria(@Param("localId") Long localId);
}
