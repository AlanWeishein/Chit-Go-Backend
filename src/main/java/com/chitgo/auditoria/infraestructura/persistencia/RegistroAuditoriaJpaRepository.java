package com.chitgo.auditoria.infraestructura.persistencia;

import java.util.Optional;

import org.springframework.data.repository.Repository;

import com.chitgo.auditoria.dominio.RegistroAuditoria;

/**
 * Solo guardar y leer: un registro de auditoría no se edita ni se borra desde la aplicación (CP-29-04). Por
 * eso no extiende JpaRepository, que trae los delete.
 */
interface RegistroAuditoriaJpaRepository extends Repository<RegistroAuditoria, Long> {

    RegistroAuditoria save(RegistroAuditoria registro);

    Optional<RegistroAuditoria> findById(Long id);
}
