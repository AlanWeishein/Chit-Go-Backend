package com.chitgo.pedidos.infraestructura.persistencia;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chitgo.pedidos.dominio.Cuenta;
import com.chitgo.pedidos.dominio.EstadoCuenta;

interface CuentaJpaRepository extends JpaRepository<Cuenta, Long> {

    Optional<Cuenta> findFirstByMesaIdAndEstadoNot(Long mesaId, EstadoCuenta estado);
}
