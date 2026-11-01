package com.chitgo.pedidos.infraestructura.persistencia;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.chitgo.pedidos.dominio.EstadoPedido;
import com.chitgo.pedidos.dominio.puertos.PedidosDemorados;

@Repository
class PedidosDemoradosAdapter implements PedidosDemorados {

    private final PedidoJpaRepository jpa;

    PedidosDemoradosAdapter(PedidoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<PedidoPendiente> pendientesEnviadosEntre(Instant despuesDe, Instant hasta) {
        return jpa.pendientesEnviadosEntre(EstadoPedido.PENDIENTE_APROBACION, despuesDe, hasta).stream()
                .map(f -> new PedidoPendiente(f.pedidoId(), f.localId(), f.mesaId()))
                .toList();
    }
}
