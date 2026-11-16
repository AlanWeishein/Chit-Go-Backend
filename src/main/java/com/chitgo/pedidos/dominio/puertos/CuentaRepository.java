package com.chitgo.pedidos.dominio.puertos;

import java.util.Optional;

import com.chitgo.pedidos.dominio.Cuenta;

public interface CuentaRepository {

    Optional<Cuenta> buscarPorId(Long id);

    /** La cuenta de la mesa que no está cerrada (como mucho hay una). */
    Optional<Cuenta> buscarNoCerradaDeMesa(Long mesaId);

    /** Guarda y fuerza el UPDATE ya: si otro la cambió antes, lanza ConflictoDeConcurrenciaException. */
    Cuenta guardar(Cuenta cuenta);
}
