package com.chitgo.usuarios.infraestructura.persistencia;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import com.chitgo.usuarios.dominio.SuscripcionPush;

interface SuscripcionPushJpaRepository extends JpaRepository<SuscripcionPush, Long> {
    List<SuscripcionPush> findByUsuarioIdAndLocalId(Long usuarioId, Long localId);

    // El UNIQUE arbitra dos registros simultáneos del mismo navegador. No se duplica por usuario.
    @Modifying
    @Query(value = """
            INSERT INTO suscripcion_push (local_id, usuario_id, endpoint, endpoint_hash, p256dh, auth, actualizada_en)
            VALUES (:localId, :usuarioId, :endpoint, :hash, :p256dh, :auth, :ahora)
            ON DUPLICATE KEY UPDATE local_id = :localId, usuario_id = :usuarioId, endpoint = :endpoint,
              p256dh = :p256dh, auth = :auth, actualizada_en = :ahora
            """, nativeQuery = true)
    void registrar(@Param("localId") Long localId, @Param("usuarioId") Long usuarioId,
            @Param("endpoint") String endpoint, @Param("hash") String hash, @Param("p256dh") String p256dh,
            @Param("auth") String auth, @Param("ahora") Instant ahora);

    void deleteByEndpointHashAndUsuarioIdAndLocalId(String hash, Long usuarioId, Long localId);
}
