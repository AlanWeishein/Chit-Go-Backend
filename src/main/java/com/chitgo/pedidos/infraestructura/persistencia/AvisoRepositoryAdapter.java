package com.chitgo.pedidos.infraestructura.persistencia;

import org.springframework.stereotype.Repository;

import com.chitgo.pedidos.dominio.Aviso;
import com.chitgo.pedidos.dominio.puertos.AvisoRepository;
import com.chitgo.pedidos.dominio.TipoAviso;
import com.chitgo.compartido.dominio.ConflictoDeConcurrenciaException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import java.util.List;
import java.util.Optional;

@Repository
class AvisoRepositoryAdapter implements AvisoRepository {

    private final AvisoJpaRepository jpa;

    AvisoRepositoryAdapter(AvisoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Aviso guardar(Aviso aviso) {
        try {
            return jpa.saveAndFlush(aviso);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConflictoDeConcurrenciaException("El aviso cambió justo antes. Volvé a mirar cómo quedó.");
        }
    }

    @Override
    public boolean hayPendiente(Long cuentaId, TipoAviso tipo) {
        return jpa.existsByCuentaIdAndTipoAndAtendidoEnIsNull(cuentaId, tipo);
    }

    @Override
    public Optional<Aviso> buscarPorId(Long id, Long localId) {
        return jpa.findByIdAndLocalId(id, localId);
    }

    @Override
    public List<Aviso> listarDelLocal(Long localId, boolean pendientes) {
        return jpa.delLocal(localId, pendientes);
    }
}
