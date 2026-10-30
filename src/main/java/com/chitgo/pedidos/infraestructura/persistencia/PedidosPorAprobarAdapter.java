package com.chitgo.pedidos.infraestructura.persistencia;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import com.chitgo.pedidos.dominio.EstadoPedido;
import com.chitgo.pedidos.dominio.puertos.PedidosPorAprobar;

@Repository
class PedidosPorAprobarAdapter implements PedidosPorAprobar {

    private final PedidoJpaRepository jpa;

    PedidosPorAprobarAdapter(PedidoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<PedidoPorAprobar> pendientesDelLocal(Long localId) {
        // Una fila por ítem: se juntan por pedido en el orden en que vienen.
        Map<Long, List<FilaPorAprobar>> porPedido = new LinkedHashMap<>();
        for (FilaPorAprobar fila : jpa.pendientesConPlatos(localId, EstadoPedido.PENDIENTE_APROBACION)) {
            porPedido.computeIfAbsent(fila.pedidoId(), id -> new ArrayList<>()).add(fila);
        }
        return porPedido.values().stream().map(PedidosPorAprobarAdapter::aPedido).toList();
    }

    private static PedidoPorAprobar aPedido(List<FilaPorAprobar> filas) {
        FilaPorAprobar primera = filas.get(0);
        return new PedidoPorAprobar(primera.pedidoId(), primera.mesa(), primera.enviadoEn(),
                filas.stream()
                        .map(f -> new ItemPorAprobar(f.platoId(), f.plato(), f.cantidad(), f.observacion(), f.disponible()))
                        .toList());
    }
}
