package com.chitgo.auditoria.infraestructura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chitgo.auditoria.dominio.RegistroAuditoria;

interface RegistroAuditoriaJpaRepository extends JpaRepository<RegistroAuditoria, Long> {
}
