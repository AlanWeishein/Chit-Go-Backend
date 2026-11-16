package com.chitgo.menu.infraestructura.persistencia;

import java.util.Collection;
import java.util.List;

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
}
