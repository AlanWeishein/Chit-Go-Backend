package com.chitgo.usuarios.infraestructura.persistencia;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.chitgo.usuarios.dominio.Ausencia;
import com.chitgo.usuarios.dominio.Empleado;
import com.chitgo.usuarios.dominio.PaginaDe;
import com.chitgo.usuarios.dominio.Turno;
import com.chitgo.usuarios.dominio.puertos.EmpleadoRepository;

@Repository
class EmpleadoRepositoryAdapter implements EmpleadoRepository {

    private final EmpleadoJpaRepository jpa;

    EmpleadoRepositoryAdapter(EmpleadoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Empleado guardar(Empleado empleado) {
        return jpa.save(empleado);
    }

    @Override
    public Optional<Empleado> buscarPorId(Long id, Long localId) {
        return jpa.buscarGestionable(id, localId, Empleado.ROLES_GESTIONABLES);
    }

    @Override
    public Optional<Empleado> buscarParaModificar(Long id, Long localId) {
        return jpa.buscarGestionableConBloqueo(id, localId, Empleado.ROLES_GESTIONABLES);
    }

    @Override
    public PaginaDe<Empleado> listarPagina(Long localId, int page, int size) {
        Page<Empleado> pagina = jpa.pagina(localId, Empleado.ROLES_GESTIONABLES, PageRequest.of(page, size));
        return new PaginaDe<>(pagina.getContent(), pagina.getTotalElements());
    }

    @Override
    public List<Turno> turnosDe(Collection<Long> empleadoIds, Long localId) {
        if (empleadoIds.isEmpty()) {
            return List.of();
        }
        return jpa.turnosDe(empleadoIds, localId);
    }

    @Override
    public Set<Long> conAusenciaVigente(Collection<Long> empleadoIds, Long localId, LocalDate hoy) {
        if (empleadoIds.isEmpty()) {
            return Set.of();
        }
        return jpa.conAusenciaVigente(empleadoIds, localId, hoy);
    }

    @Override
    public List<Ausencia> ausenciasDe(Long empleadoId, Long localId) {
        return jpa.ausenciasDe(empleadoId, localId);
    }
}
