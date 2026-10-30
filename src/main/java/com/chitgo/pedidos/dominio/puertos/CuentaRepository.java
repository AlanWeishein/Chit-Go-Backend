package com.chitgo.pedidos.dominio.puertos;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.chitgo.pedidos.dominio.Cuenta;
import com.chitgo.pedidos.dominio.EstadoCuenta;

public interface CuentaRepository {

    /** Del local: una de otro local da lo mismo que una que no existe. */
    Optional<Cuenta> buscarPorId(Long id, Long localId);

    /** Las del local en esos estados, con la mesa, en una sola consulta y sin orden. */
    List<Cuenta> listarDelLocal(Long localId, Collection<EstadoCuenta> estados);

    /** La cuenta de la mesa que no está cerrada (como mucho hay una). */
    Optional<Cuenta> buscarNoCerradaDeMesa(Long mesaId);

    /** Guarda y fuerza el UPDATE ya: si otro la cambió antes, lanza ConflictoDeConcurrenciaException. */
    Cuenta guardar(Cuenta cuenta);

    /**
     * Sube la versión aunque la cuenta no haya cambiado y fuerza el UPDATE ya, con la versión que leyó esta
     * transacción. Mandar un pedido la usa para chocar con el cierre de la cuenta. Si otro la cambió antes, o el
     * UPDATE queda trabado en un bloqueo, lanza ConflictoDeConcurrenciaException. La versión de la entidad en
     * memoria queda vieja: después de esto no hay que guardar esa cuenta en la misma transacción.
     */
    void subirVersion(Cuenta cuenta);
}
