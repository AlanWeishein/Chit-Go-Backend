package com.chitgo.usuarios.infraestructura.persistencia;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chitgo.usuarios.dominio.DiaSemana;
import com.chitgo.usuarios.dominio.Empleado;

interface EmpleadoJpaRepository extends JpaRepository<Empleado, Long> {

    /**
     * Los empleados asignados a la mesa, activos, con un turno que cubre este día y esta hora y sin una
     * ausencia vigente (hasta NULL = sin fecha de vuelta). Un turno que cruza la medianoche todavía no
     * se contempla (pendiente HU-14).
     */
    @Query("""
            SELECT e.id FROM Empleado e JOIN e.mesasAsignadas mm
            WHERE mm.id.mesaId = :mesaId AND e.usuario.activo = TRUE
              AND EXISTS (SELECT t.id FROM Turno t WHERE t.empleado = e AND t.diaSemana = :dia
                          AND t.horaInicio <= :hora AND t.horaFin > :hora)
              AND NOT EXISTS (SELECT a.id FROM Ausencia a WHERE a.empleado = e AND a.desde <= :fecha
                              AND (a.hasta IS NULL OR a.hasta >= :fecha))
            ORDER BY e.id
            """)
    List<Long> mozosDeTurno(@Param("mesaId") Long mesaId, @Param("dia") DiaSemana dia,
            @Param("hora") LocalTime hora, @Param("fecha") LocalDate fecha);
}
