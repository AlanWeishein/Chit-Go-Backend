package com.chitgo.pedidos.infraestructura.persistencia;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chitgo.pedidos.dominio.EstadoPedido;
import com.chitgo.pedidos.dominio.Pedido;

interface PedidoJpaRepository extends JpaRepository<Pedido, Long> {

    Optional<Pedido> findByIdAndLocalId(Long id, Long localId);

    Optional<Pedido> findByUuidCliente(String uuidCliente);

    @Query("SELECT DISTINCT p FROM Pedido p LEFT JOIN FETCH p.items "
            + "WHERE p.cuenta.id = :cuentaId ORDER BY p.enviadoEn, p.id")
    List<Pedido> deLaCuenta(@Param("cuentaId") Long cuentaId);

    @Query("SELECT DISTINCT p FROM Pedido p JOIN FETCH p.cuenta c JOIN FETCH c.mesa LEFT JOIN FETCH p.items "
            + "WHERE p.localId = :localId AND p.estado IN :estados")
    List<Pedido> conEstados(@Param("localId") Long localId, @Param("estados") Collection<EstadoPedido> estados);
}
