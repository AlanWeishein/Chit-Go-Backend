package com.chitgo.pedidos.infraestructura.persistencia;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Repository;

import com.chitgo.menu.dominio.puertos.PedidosConPlato;
import com.chitgo.pedidos.dominio.EstadoPedido;

/** El puerto de menu para HU-09: lo implementa pedidos, que es quien conoce los pedidos (decidido el 29/10). */
@Repository
class PedidosConPlatoAdapter implements PedidosConPlato {

    /** Los que Caja puede cancelar. En preparación sigue en el KDS; Listo y Servido ya se cocinaron (29/10). */
    private static final Set<EstadoPedido> CANCELABLES =
            Set.of(EstadoPedido.PENDIENTE_APROBACION, EstadoPedido.APROBADO);

    private final PedidoJpaRepository jpa;

    PedidosConPlatoAdapter(PedidoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<PedidoAfectado> afectadosPor(Long platoId, Long localId) {
        // Una fila por ítem: se juntan por pedido en el orden en que vienen.
        Map<Long, List<FilaAfectada>> porPedido = new LinkedHashMap<>();
        for (FilaAfectada fila : jpa.conPlato(platoId, localId, CANCELABLES)) {
            porPedido.computeIfAbsent(fila.pedidoId(), id -> new ArrayList<>()).add(fila);
        }
        return porPedido.values().stream().map(PedidosConPlatoAdapter::aPedido).toList();
    }

    private static PedidoAfectado aPedido(List<FilaAfectada> filas) {
        FilaAfectada primera = filas.get(0);
        return new PedidoAfectado(primera.pedidoId(), primera.mesa(), primera.estado().name(),
                filas.stream().map(f -> new PlatoDelPedido(f.platoId(), f.plato(), f.cantidad())).toList());
    }
}
