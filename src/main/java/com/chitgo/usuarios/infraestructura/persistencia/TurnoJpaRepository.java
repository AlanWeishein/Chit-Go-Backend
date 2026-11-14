package com.chitgo.usuarios.infraestructura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chitgo.usuarios.dominio.Turno;

interface TurnoJpaRepository extends JpaRepository<Turno, Long> {

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Turno t WHERE t.empleado.id = :empleadoId AND t.localId = :localId")
    int borrarDe(@Param("empleadoId") Long empleadoId, @Param("localId") Long localId);
}
