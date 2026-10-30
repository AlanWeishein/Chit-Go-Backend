package com.chitgo.pedidos.dominio.puertos;

import java.time.Instant;
import java.util.List;

/**
 * Lectura de la cola de Caja (HU-07). Es una consulta con JOIN a los platos de menu, sin relación JPA entre
 * los módulos (decisión B del 20/10): devuelve solo lo que muestra P-08, no entidades.
 */
public interface PedidosPorAprobar {

    /** Los pendientes de aprobación del local, con la mesa y los ítems. */
    List<PedidoPorAprobar> pendientesDelLocal(Long localId);

    /** @param mesa el número de la mesa, no su id */
    record PedidoPorAprobar(Long id, String mesa, Instant enviadoEn, List<ItemPorAprobar> items) {
    }

    /** @param disponible si el plato sigue activo en la carta */
    record ItemPorAprobar(Long platoId, String plato, int cantidad, String observacion, boolean disponible) {
    }
}
