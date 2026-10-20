package com.chitgo.auditoria.infraestructura.persistencia;

import org.springframework.stereotype.Repository;

import com.chitgo.auditoria.dominio.RegistroAuditoria;
import com.chitgo.auditoria.dominio.puertos.Auditor;

/** PROVISORIO: se reemplaza por el registro de auditoría de HU-15. */
@Repository
class AuditorAdapter implements Auditor {

    private final RegistroAuditoriaJpaRepository jpa;

    AuditorAdapter(RegistroAuditoriaJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void registrar(Long localId, Long usuarioId, String accion, String entidad, Long entidadId,
            String valorAnterior, String valorNuevo) {
        jpa.save(new RegistroAuditoria(localId, usuarioId, accion, entidad, entidadId, valorAnterior, valorNuevo));
    }
}
