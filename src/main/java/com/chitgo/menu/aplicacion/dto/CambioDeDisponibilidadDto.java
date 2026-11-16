package com.chitgo.menu.aplicacion.dto;

import java.util.List;

import com.chitgo.menu.dominio.puertos.PedidosConPlato.PedidoAfectado;

/**
 * La respuesta de PATCH /platos/{id}/disponibilidad (29/10).
 *
 * @param afectados los pedidos que Caja puede cancelar; vacía si el plato quedó disponible
 */
public record CambioDeDisponibilidadDto(Long platoId, boolean disponible, List<PedidoAfectado> afectados) {
}
