package com.chitgo.menu.dominio.puertos;

import java.util.List;

/**
 * Los pedidos en curso que tienen un plato, para el aviso de HU-09. Lo declara menu y lo implementa pedidos, así la
 * dependencia sigue yendo solo de pedidos a menu (decidido el 29/10). Devuelve solo lo que arma el diálogo de
 * afectados, con tipos de Java: menu no conoce las clases de pedidos.
 */
public interface PedidosConPlato {

    /**
     * Los pedidos del local en PENDIENTE_APROBACION o APROBADO que tienen ese plato, los únicos que se pueden
     * cancelar. En preparación sigue en el KDS (CP-23-04). Del más viejo al más nuevo.
     */
    List<PedidoAfectado> afectadosPor(Long platoId, Long localId);

    /**
     * @param mesa   el número de la mesa, no su id
     * @param estado PENDIENTE_APROBACION o APROBADO
     * @param platos todos los ítems del pedido, no solo el plato que se apagó
     */
    record PedidoAfectado(Long id, String mesa, String estado, List<PlatoDelPedido> platos) {
    }

    record PlatoDelPedido(Long platoId, String nombre, int cantidad) {
    }
}
