package com.chitgo.pedidos.infraestructura.persistencia;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chitgo.pedidos.dominio.Cuenta;
import com.chitgo.pedidos.dominio.EstadoCuenta;

interface CuentaJpaRepository extends JpaRepository<Cuenta, Long> {

    Optional<Cuenta> findByIdAndLocalId(Long id, Long localId);

    Optional<Cuenta> findFirstByMesaIdAndEstadoNot(Long mesaId, EstadoCuenta estado);

    /**
     * Sube la versión ya, con el UPDATE: devuelve 0 filas si otro la cambió antes. No se usa
     * OPTIMISTIC_FORCE_INCREMENT porque Hibernate lo ejecuta recién antes del commit (ahí el choque saldría como 500
     * y el lock de la fila se tomaría tarde).
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Cuenta c SET c.version = c.version + 1 WHERE c.id = :id AND c.version = :version")
    int subirVersion(@Param("id") Long id, @Param("version") Integer version);
}
