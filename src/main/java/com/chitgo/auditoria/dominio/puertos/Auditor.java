package com.chitgo.auditoria.dominio.puertos;

/**
 * Registro de auditoría de las acciones del personal (RF-36, CU-33). Lo llaman los Services de los otros
 * módulos dentro de su propia transacción: el registro se guarda junto con la acción, y si falla la escritura
 * del registro la acción tampoco se guarda. Lo que hace el cliente anónimo no se audita.
 */
public interface Auditor {

    /**
     * Guarda quién hizo qué, sobre qué y cuándo, con los valores de antes y de después pasados a JSON.
     *
     * <p>Tiene que haber una transacción abierta: sin ella tira {@code IllegalTransactionStateException} y no
     * guarda nada.
     *
     * @param localId       el local donde pasó. Lo pasa quien llama y no sale de la sesión, porque no siempre es
     *                      el de quien actúa: el Gerente/Dueño no tiene local, y en HU-16 va el del Admin afectado
     * @param usuarioId     quien hizo la acción
     * @param accion        qué hizo, ej. APROBAR_PEDIDO
     * @param entidad       sobre qué, ej. PEDIDO
     * @param entidadId     el id de lo que cambió, o NULL si no hay uno
     * @param valorAnterior cómo estaba (un DTO o un Map), o NULL en las altas
     * @param valorNuevo    cómo quedó (un DTO o un Map), o NULL
     */
    void registrar(Long localId, Long usuarioId, String accion, String entidad, Long entidadId,
            Object valorAnterior, Object valorNuevo);
}
