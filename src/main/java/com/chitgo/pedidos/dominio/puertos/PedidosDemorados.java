package com.chitgo.pedidos.dominio.puertos;

import java.time.Instant;
import java.util.List;

/**
 * Lectura para la alerta de los 3 minutos (HU-08): los pendientes de todos los locales, porque la revisión no
 * corre en nombre de nadie. Trae solo lo que lleva el aviso, sin los ítems.
 */
public interface PedidosDemorados {

    /** Los PENDIENTE_APROBACION con {@code despuesDe < enviadoEn <= hasta}. */
    List<PedidoPendiente> pendientesEnviadosEntre(Instant despuesDe, Instant hasta);

    /** @param mesaId el id de la mesa, no su número */
    record PedidoPendiente(Long id, Long localId, Long mesaId) {
    }
}
