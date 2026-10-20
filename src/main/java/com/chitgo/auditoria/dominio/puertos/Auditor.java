package com.chitgo.auditoria.dominio.puertos;

/**
 * PROVISORIO: lo agregó German para poder auditar los cambios de Cocina (RF-36). Se reemplaza por el de
 * HU-15 cuando esté; si cambia la firma, se ajusta ColaDeCocinaService. Se llama dentro de la transacción
 * de la acción, así si la auditoría falla la acción tampoco se guarda.
 */
public interface Auditor {

    /**
     * @param accion        en mayúsculas, por ejemplo INICIAR_PREPARACION
     * @param entidad       por ejemplo PEDIDO
     * @param valorAnterior JSON, por ejemplo {"estado":"APROBADO"}
     */
    void registrar(Long localId, Long usuarioId, String accion, String entidad, Long entidadId,
                   String valorAnterior, String valorNuevo);
}
