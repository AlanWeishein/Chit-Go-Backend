package com.chitgo.pedidos.dominio.puertos;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.chitgo.pedidos.dominio.Pedido;

public interface PedidoRepository {

    /**
     * Guarda y fuerza el INSERT o el UPDATE ya, para que los conflictos salten acá y no al cerrar la transacción.
     *
     * @throws com.chitgo.pedidos.dominio.PedidoDuplicadoException   si el uuidCliente ya existe
     * @throws com.chitgo.compartido.dominio.ConflictoDeConcurrenciaException si otro cambió el pedido antes
     */
    Pedido guardar(Pedido pedido);

    /** Del local: uno de otro local da lo mismo que uno que no existe. */
    Optional<Pedido> buscarPorId(Long id, Long localId);

    Optional<Pedido> buscarPorUuidCliente(String uuidCliente);

    /** Los de la cuenta, con sus ítems, del más viejo al más nuevo. */
    List<Pedido> listarDeLaCuenta(Long cuentaId);

    /** Los de todas esas cuentas, con sus ítems, en una sola consulta (cuentas por cobrar, HU-10). */
    List<Pedido> listarDeLasCuentas(Collection<Long> cuentaIds);

    /** Aprobados, en preparación y listos del local, con la cuenta, la mesa y los ítems. */
    List<Pedido> listarActivosDeCocina(Long localId);
}
