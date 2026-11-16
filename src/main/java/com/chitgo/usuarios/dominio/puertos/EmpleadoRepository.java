package com.chitgo.usuarios.dominio.puertos;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.chitgo.usuarios.dominio.Ausencia;
import com.chitgo.usuarios.dominio.Empleado;
import com.chitgo.usuarios.dominio.PaginaDe;
import com.chitgo.usuarios.dominio.Turno;

/**
 * La gestión de empleados del Admin (HU-14). Todo se filtra por local, y solo ve a los Mozo, Caja y Cocina
 * (ver {@link Empleado#ROLES_GESTIONABLES}): el Admin del local no es un empleado que se gestione.
 */
public interface EmpleadoRepository {

    Empleado guardar(Empleado empleado);

    Optional<Empleado> buscarPorId(Long id, Long localId);

    /** Con la fila bloqueada hasta el fin de la transacción: dos cambios al mismo tiempo no se pisan. */
    Optional<Empleado> buscarParaModificar(Long id, Long localId);

    /** Página {@code page} (desde 0) del local, ordenada por nombre y después por id. */
    PaginaDe<Empleado> listarPagina(Long localId, int page, int size);

    /** Los turnos de esos empleados, en una sola consulta. */
    List<Turno> turnosDe(Collection<Long> empleadoIds, Long localId);

    /** Cuáles de esos empleados tienen una ausencia que cubre {@code hoy} (hasta NULL = sin fecha de vuelta). */
    Set<Long> conAusenciaVigente(Collection<Long> empleadoIds, Long localId, LocalDate hoy);

    /** Las ausencias del empleado, de la más nueva a la más vieja. */
    List<Ausencia> ausenciasDe(Long empleadoId, Long localId);

    /** Borra los turnos del empleado y guarda estos en su lugar. */
    void reemplazarTurnos(Long empleadoId, Long localId, List<Turno> turnos);

    Ausencia guardarAusencia(Ausencia ausencia);

    /** La ausencia, si es de ese empleado y de ese local. */
    Optional<Ausencia> buscarAusencia(Long ausenciaId, Long empleadoId, Long localId);
}
