package com.chitgo.usuarios.infraestructura.persistencia;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chitgo.usuarios.dominio.Ausencia;
import com.chitgo.usuarios.dominio.DiaSemana;
import com.chitgo.usuarios.dominio.Empleado;
import com.chitgo.usuarios.dominio.Turno;

interface EmpleadoJpaRepository extends JpaRepository<Empleado, Long> {

    @Query("SELECT mm.id.mesaId FROM Empleado e JOIN e.mesasAsignadas mm "
            + "WHERE e.usuario.id = :usuarioId AND e.localId = :localId AND mm.localId = :localId")
    Set<Long> mesasDe(@Param("usuarioId") Long usuarioId, @Param("localId") Long localId);

    @Query("SELECT e.usuario.id FROM Empleado e WHERE e.id = :empleadoId AND e.localId = :localId "
            + "AND e.usuario.activo = TRUE AND e.usuario.rol.codigo = 'MOZO'")
    Optional<Long> usuarioDelEmpleado(@Param("empleadoId") Long empleadoId, @Param("localId") Long localId);

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

    // HU-14: la gestión del Admin. Solo los roles que se dan de alta, así el Admin de la seed no aparece.

    @Query(value = """
            SELECT e FROM Empleado e JOIN FETCH e.usuario u JOIN FETCH u.rol r
            WHERE e.localId = :localId AND r.codigo IN :roles
            ORDER BY u.nombre ASC, e.id ASC
            """,
            countQuery = """
            SELECT COUNT(e) FROM Empleado e WHERE e.localId = :localId AND e.usuario.rol.codigo IN :roles
            """)
    Page<Empleado> pagina(@Param("localId") Long localId, @Param("roles") Collection<String> roles,
            Pageable pageable);

    @Query("""
            SELECT e FROM Empleado e JOIN FETCH e.usuario u JOIN FETCH u.rol r
            WHERE e.id = :id AND e.localId = :localId AND r.codigo IN :roles
            """)
    Optional<Empleado> buscarGestionable(@Param("id") Long id, @Param("localId") Long localId,
            @Param("roles") Collection<String> roles);

    /** Con la fila (y la del usuario) bloqueada: dos bajas al mismo tiempo no auditan dos veces. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT e FROM Empleado e JOIN FETCH e.usuario u JOIN FETCH u.rol r
            WHERE e.id = :id AND e.localId = :localId AND r.codigo IN :roles
            """)
    Optional<Empleado> buscarGestionableConBloqueo(@Param("id") Long id, @Param("localId") Long localId,
            @Param("roles") Collection<String> roles);

    @Query("SELECT t FROM Turno t WHERE t.empleado.id IN :ids AND t.localId = :localId")
    List<Turno> turnosDe(@Param("ids") Collection<Long> ids, @Param("localId") Long localId);

    @Query("""
            SELECT DISTINCT a.empleado.id FROM Ausencia a
            WHERE a.empleado.id IN :ids AND a.localId = :localId AND a.desde <= :hoy
              AND (a.hasta IS NULL OR a.hasta >= :hoy)
            """)
    Set<Long> conAusenciaVigente(@Param("ids") Collection<Long> ids, @Param("localId") Long localId,
            @Param("hoy") LocalDate hoy);

    @Query("""
            SELECT a FROM Ausencia a WHERE a.empleado.id = :empleadoId AND a.localId = :localId
            ORDER BY a.desde DESC, a.id DESC
            """)
    List<Ausencia> ausenciasDe(@Param("empleadoId") Long empleadoId, @Param("localId") Long localId);

    /**
     * Cuáles de esos ids son mesas del local. Mesa es de pedidos: se nombra en el JPQL sin importar la clase, como
     * PedidoJpaRepository une Plato (la excepción del punto 2 del 20/10).
     */
    @Query("SELECT m.id FROM Mesa m WHERE m.id IN :ids AND m.localId = :localId")
    Set<Long> mesasDelLocal(@Param("ids") Collection<Long> ids, @Param("localId") Long localId);
}
