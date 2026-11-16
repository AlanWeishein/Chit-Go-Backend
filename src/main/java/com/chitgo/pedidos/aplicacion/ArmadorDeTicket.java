package com.chitgo.pedidos.aplicacion;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.chitgo.pedidos.aplicacion.dto.ItemDeTicketDto;
import com.chitgo.pedidos.aplicacion.dto.TicketDto;
import com.chitgo.pedidos.dominio.Cuenta;
import com.chitgo.pedidos.dominio.ItemPedido;
import com.chitgo.pedidos.dominio.Pedido;
import com.chitgo.pedidos.dominio.Ticket;
import com.chitgo.usuarios.dominio.puertos.LocalRepository;

/**
 * Arma el TicketDto de una cuenta (HU-10). Se llama dentro de la transacción del Service, como ArmadorDePedidos,
 * porque la mesa y los ítems se cargan de a poco. El nombre del local sale de usuarios por su puerto y los de los
 * platos se leen por lote a través de menu (punto 2 del 20/10).
 */
@Component
class ArmadorDeTicket {

    private final LocalRepository locales;
    private final ArmadorDePedidos armador;

    ArmadorDeTicket(LocalRepository locales, ArmadorDePedidos armador) {
        this.locales = locales;
        this.armador = armador;
    }

    /**
     * Entran los pedidos que no están Rechazados ni Cancelados. El mismo plato al mismo precio, de uno o de varios
     * pedidos, va en un solo renglón (2 Milanesas), en el orden en que aparece. Si el precio cambió entre dos
     * pedidos, son dos renglones.
     */
    TicketDto armar(Ticket ticket, Cuenta cuenta, List<Pedido> pedidos) {
        List<Pedido> cobrados = pedidos.stream().filter(Pedido::cuentaEnElTotal).toList();
        Map<Long, String> nombres = armador.nombresDePlatos(cuenta.getLocalId(), cobrados);

        Map<ClaveDeRenglon, Integer> cantidades = new LinkedHashMap<>();
        for (Pedido pedido : cobrados) {
            for (ItemPedido item : pedido.getItems()) {
                cantidades.merge(new ClaveDeRenglon(item.getPlatoId(), item.getPrecioUnitario()), (int) item.getCantidad(),
                        Integer::sum);
            }
        }
        List<ItemDeTicketDto> items = new ArrayList<>();
        cantidades.forEach((clave, cantidad) -> items.add(new ItemDeTicketDto(nombres.get(clave.platoId()), cantidad,
                clave.precio(), clave.precio().multiply(BigDecimal.valueOf(cantidad)))));

        String local = locales.buscarPorId(cuenta.getLocalId())
                .orElseThrow(() -> new IllegalStateException("El local de la cuenta no existe"))
                .getNombre();
        return new TicketDto(ticket.getId(), local, cuenta.getMesa().getNumero(), ticket.getEmitidoEn(), items,
                ticket.getTotal(), TicketDto.LETRA, TicketDto.LEYENDA);
    }

    private record ClaveDeRenglon(Long platoId, BigDecimal precio) {
    }
}
