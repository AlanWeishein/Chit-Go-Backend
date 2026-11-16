package com.chitgo.usuarios.infraestructura.persistencia;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chitgo.usuarios.dominio.Ausencia;

interface AusenciaJpaRepository extends JpaRepository<Ausencia, Long> {

    @Query("SELECT a FROM Ausencia a WHERE a.id = :id AND a.empleado.id = :empleadoId AND a.localId = :localId")
    Optional<Ausencia> buscar(@Param("id") Long id, @Param("empleadoId") Long empleadoId,
            @Param("localId") Long localId);
}
