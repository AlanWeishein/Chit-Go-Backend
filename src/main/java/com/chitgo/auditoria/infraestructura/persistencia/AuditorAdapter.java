package com.chitgo.auditoria.infraestructura.persistencia;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import com.chitgo.auditoria.dominio.RegistroAuditoria;
import com.chitgo.auditoria.dominio.puertos.Auditor;

/**
 * MANDATORY: se suma a la transacción de quien llama y, si no hay ninguna, falla sin guardar nada. Si la
 * escritura falla, la excepción marca esa transacción para deshacerse, así que la acción tampoco se guarda
 * (CP-33-03).
 */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
class AuditorAdapter implements Auditor {

    private final RegistroAuditoriaJpaRepository jpa;
    private final ObjectMapper json;

    AuditorAdapter(RegistroAuditoriaJpaRepository jpa, ObjectMapper json) {
        this.jpa = jpa;
        this.json = json;
    }

    @Override
    public void registrar(Long localId, Long usuarioId, String accion, String entidad, Long entidadId,
            Object valorAnterior, Object valorNuevo) {
        jpa.save(new RegistroAuditoria(localId, usuarioId, accion, entidad, entidadId, aJson(valorAnterior),
                aJson(valorNuevo)));
    }

    /** Un valor NULL queda NULL en la columna, no como el JSON {@code null}. */
    private String aJson(Object valor) {
        return valor == null ? null : json.writeValueAsString(valor);
    }
}
