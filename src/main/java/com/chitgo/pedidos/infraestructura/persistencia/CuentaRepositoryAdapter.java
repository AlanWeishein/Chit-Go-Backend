package com.chitgo.pedidos.infraestructura.persistencia;

import java.util.Optional;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import com.chitgo.compartido.dominio.ConflictoDeConcurrenciaException;
import com.chitgo.pedidos.dominio.Cuenta;
import com.chitgo.pedidos.dominio.EstadoCuenta;
import com.chitgo.pedidos.dominio.puertos.CuentaRepository;

@Repository
class CuentaRepositoryAdapter implements CuentaRepository {

    private static final String CAMBIO_JUSTO_ANTES = "La cuenta cambió justo antes. Probá de nuevo.";

    private final CuentaJpaRepository jpa;

    CuentaRepositoryAdapter(CuentaJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Cuenta> buscarPorId(Long id, Long localId) {
        return jpa.findByIdAndLocalId(id, localId);
    }

    @Override
    public Optional<Cuenta> buscarNoCerradaDeMesa(Long mesaId) {
        return jpa.findFirstByMesaIdAndEstadoNot(mesaId, EstadoCuenta.CERRADA);
    }

    @Override
    public Cuenta guardar(Cuenta cuenta) {
        try {
            return jpa.saveAndFlush(cuenta);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConflictoDeConcurrenciaException(CAMBIO_JUSTO_ANTES);
        }
    }

    @Override
    public void subirVersion(Cuenta cuenta) {
        try {
            if (jpa.subirVersion(cuenta.getId(), cuenta.getVersion()) == 0) {
                throw new ConflictoDeConcurrenciaException(CAMBIO_JUSTO_ANTES);
            }
        } catch (PessimisticLockingFailureException e) {
            // Un deadlock (1213) o un lock wait timeout de InnoDB contra otro envío o el cierre: también es un choque.
            throw new ConflictoDeConcurrenciaException(CAMBIO_JUSTO_ANTES);
        }
    }
}
