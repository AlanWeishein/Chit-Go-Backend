package com.chitgo.pedidos.infraestructura.persistencia;

import java.time.Instant;
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

    @Query("SELECT DISTINCT p FROM Pedido p LEFT JOIN FETCH p.items "
            + "WHERE p.cuenta.id IN :cuentaIds ORDER BY p.enviadoEn, p.id")
    List<Pedido> deLasCuentas(@Param("cuentaIds") Collection<Long> cuentaIds);

    @Query("SELECT DISTINCT p FROM Pedido p JOIN FETCH p.cuenta c JOIN FETCH c.mesa LEFT JOIN FETCH p.items "
            + "WHERE p.localId = :localId AND p.estado IN :estados")
    List<Pedido> conEstados(@Param("localId") Long localId, @Param("estados") Collection<EstadoPedido> estados);

    /**
     * La cola de Caja en una sola consulta, una fila por ítem. Plato es de menu: se une por platoId, sin
     * relación JPA (decisión B del 20/10). La FK de item_pedido asegura que el plato existe.
     */
    @Query("SELECT new com.chitgo.pedidos.infraestructura.persistencia.FilaPorAprobar("
            + "p.id, m.numero, p.enviadoEn, i.platoId, pl.nombre, i.cantidad, i.observacion, pl.activo) "
            + "FROM Pedido p JOIN p.cuenta c JOIN c.mesa m JOIN p.items i "
            + "JOIN Plato pl ON pl.id = i.platoId AND pl.localId = p.localId "
            + "WHERE p.localId = :localId AND p.estado = :estado "
            + "ORDER BY p.enviadoEn, p.id, i.id")
    List<FilaPorAprobar> pendientesConPlatos(@Param("localId") Long localId, @Param("estado") EstadoPedido estado);

    /**
     * Los pedidos del local en esos estados que tienen el plato, una fila por cada uno de sus ítems (también los de
     * otros platos). Plato se une por platoId, como en la cola de Caja.
     */
    @Query("SELECT new com.chitgo.pedidos.infraestructura.persistencia.FilaAfectada("
            + "p.id, m.numero, p.estado, i.platoId, pl.nombre, i.cantidad) "
            + "FROM Pedido p JOIN p.cuenta c JOIN c.mesa m JOIN p.items i "
            + "JOIN Plato pl ON pl.id = i.platoId AND pl.localId = p.localId "
            + "WHERE p.localId = :localId AND p.estado IN :estados "
            + "AND p.id IN (SELECT ic.pedido.id FROM ItemPedido ic WHERE ic.platoId = :platoId) "
            + "ORDER BY p.enviadoEn, p.id, i.id")
    List<FilaAfectada> conPlato(@Param("platoId") Long platoId, @Param("localId") Long localId,
                                @Param("estados") Collection<EstadoPedido> estados);

    /** La alerta de los 3 minutos (HU-08): de todos los locales, en la ventana (despuesDe, hasta], sin ítems. */
    @Query("SELECT new com.chitgo.pedidos.infraestructura.persistencia.FilaPendiente(p.id, p.localId, c.mesa.id) "
            + "FROM Pedido p JOIN p.cuenta c "
            + "WHERE p.estado = :estado AND p.enviadoEn > :despuesDe AND p.enviadoEn <= :hasta "
            + "ORDER BY p.enviadoEn, p.id")
    List<FilaPendiente> pendientesEnviadosEntre(@Param("estado") EstadoPedido estado,
                                                @Param("despuesDe") Instant despuesDe, @Param("hasta") Instant hasta);
}
