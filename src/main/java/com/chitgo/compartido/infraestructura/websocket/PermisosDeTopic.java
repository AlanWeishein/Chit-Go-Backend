package com.chitgo.compartido.infraestructura.websocket;

import java.util.List;
import java.util.Map;

import com.chitgo.usuarios.dominio.CodigosDePermiso;

/**
 * Qué permiso hace falta para suscribirse a cada topic del personal (/topic/local/{localId}/{topic}); con
 * alguno de la lista alcanza. /alertas es de Caja y del Admin. /pantalla queda afuera a propósito: la Pantalla de
 * Estadísticas no tiene login y entra con su propio token (Sprint 3).
 */
final class PermisosDeTopic {

    private PermisosDeTopic() {
    }

    private static final Map<String, List<String>> PERMISOS = Map.of(
            "caja/pedidos", List.of(CodigosDePermiso.PEDIDO_APROBAR_RECHAZAR),
            "cocina/cola", List.of(CodigosDePermiso.PEDIDO_CAMBIAR_ESTADO_COCINA),
            "mozo/avisos", List.of(CodigosDePermiso.AVISO_ATENDER),
            "alertas", List.of(CodigosDePermiso.PEDIDO_APROBAR_RECHAZAR, CodigosDePermiso.EMPLEADO_GESTIONAR));

    /** Vacío si el topic no existe o no es para el personal con login. */
    static List<String> requeridos(String topic) {
        return PERMISOS.getOrDefault(topic, List.of());
    }
}
