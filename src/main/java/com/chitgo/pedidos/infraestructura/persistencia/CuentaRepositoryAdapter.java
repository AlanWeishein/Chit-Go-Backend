package com.chitgo.pedidos.infraestructura.persistencia;

import java.util.Optional;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import com.chitgo.compartido.dominio.ConflictoDeConcurrenciaException;
import com.chitgo.pedidos.dominio.Cuenta;
import com.chitgo.pedidos.dominio.EstadoCuenta;
import com.chitgo.pedidos.dominio.puertos.CuentaRepository;

@Repository
class CuentaRepositoryAdapter implements CuentaRepository {

    private final CuentaJpaRepository jpa;

    CuentaRepositoryAdapter(CuentaJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Cuenta> buscarPorId(Long id) {
        return jpa.findById(id);
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
            throw new ConflictoDeConcurrenciaException("La cuenta cambió justo antes. Probá de nuevo.");
        }
    }
}
