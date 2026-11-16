package com.chitgo.pedidos.infraestructura.persistencia;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import com.chitgo.compartido.dominio.ConflictoDeConcurrenciaException;
import com.chitgo.pedidos.dominio.EstadoPedido;
import com.chitgo.pedidos.dominio.Pedido;
import com.chitgo.pedidos.dominio.PedidoDuplicadoException;
import com.chitgo.pedidos.dominio.puertos.PedidoRepository;

@Repository
class PedidoRepositoryAdapter implements PedidoRepository {

    private static final List<EstadoPedido> ESTADOS_DE_COCINA =
            List.of(EstadoPedido.APROBADO, EstadoPedido.EN_PREPARACION, EstadoPedido.LISTO);

    private final PedidoJpaRepository jpa;

    PedidoRepositoryAdapter(PedidoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Pedido guardar(Pedido pedido) {
        boolean esNuevo = pedido.getId() == null;
        try {
            // saveAndFlush: el INSERT o el UPDATE salen ya, así el conflicto salta acá y no al cerrar la transacción.
            return jpa.saveAndFlush(pedido);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConflictoDeConcurrenciaException("El pedido cambió de estado justo antes. Volvé a mirar cómo quedó.");
        } catch (DataIntegrityViolationException e) {
            // Solo el UNIQUE de uuid_cliente es un reintento; cualquier otra violación es un error de verdad.
            String causa = String.valueOf(e.getMostSpecificCause().getMessage());
            if (esNuevo && causa.contains("uuid_cliente")) {
                throw new PedidoDuplicadoException();
            }
            throw e;
        }
    }

    @Override
    public Optional<Pedido> buscarPorId(Long id, Long localId) {
        return jpa.findByIdAndLocalId(id, localId);
    }

    @Override
    public Optional<Pedido> buscarPorUuidCliente(String uuidCliente) {
        return jpa.findByUuidCliente(uuidCliente);
    }

    @Override
    public List<Pedido> listarDeLaCuenta(Long cuentaId) {
        return jpa.deLaCuenta(cuentaId);
    }

    @Override
    public List<Pedido> listarActivosDeCocina(Long localId) {
        return jpa.conEstados(localId, ESTADOS_DE_COCINA);
    }
}
