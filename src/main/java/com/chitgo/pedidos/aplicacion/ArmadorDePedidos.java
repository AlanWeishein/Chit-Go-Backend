package com.chitgo.pedidos.aplicacion;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.chitgo.menu.dominio.Plato;
import com.chitgo.menu.dominio.puertos.CartaRepository;
import com.chitgo.pedidos.aplicacion.dto.ItemDto;
import com.chitgo.pedidos.aplicacion.dto.PedidoDto;
import com.chitgo.pedidos.dominio.ItemPedido;
import com.chitgo.pedidos.dominio.Pedido;

/**
 * Pasa los pedidos a DTO. Se llama dentro de la transacción del Service, porque la cuenta, la mesa y los
 * ítems se cargan de a poco y con open-in-view apagado no se pueden leer después.
 */
@Component
class ArmadorDePedidos {

    private final CartaRepository carta;

    ArmadorDePedidos(CartaRepository carta) {
        this.carta = carta;
    }

    /** El nombre de cada plato de los pedidos, incluso de los que ya no están disponibles. */
    Map<Long, String> nombresDePlatos(Long localId, Collection<Pedido> pedidos) {
        Set<Long> ids = pedidos.stream()
                .flatMap(p -> p.getItems().stream())
                .map(ItemPedido::getPlatoId)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return carta.buscarPorIds(localId, ids).stream()
                .collect(Collectors.toMap(Plato::getId, Plato::getNombre));
    }

    PedidoDto aDto(Pedido pedido, Map<Long, String> nombres) {
        return new PedidoDto(pedido.getId(), pedido.getEstado().name(),
                pedido.getMotivoRechazo() == null ? null : pedido.getMotivoRechazo().name(),
                pedido.isPrioridadManual(), pedido.getEnviadoEn(), pedido.total(),
                pedido.getItems().stream()
                        .map(i -> new ItemDto(i.getPlatoId(), nombres.get(i.getPlatoId()), i.getCantidad(),
                                i.getPrecioUnitario(), i.getObservacion()))
                        .toList());
    }
}
