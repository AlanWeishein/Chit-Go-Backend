package com.chitgo.pedidos.infraestructura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chitgo.pedidos.dominio.Aviso;
import com.chitgo.pedidos.dominio.TipoAviso;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AvisoJpaRepository extends JpaRepository<Aviso, Long> {
    boolean existsByCuentaIdAndTipoAndAtendidoEnIsNull(Long cuentaId, TipoAviso tipo);

    Optional<Aviso> findByIdAndLocalId(Long id, Long localId);

    @Query("SELECT a FROM Aviso a JOIN FETCH a.mesa WHERE a.localId = :localId "
            + "AND (:pendientes = FALSE OR a.atendidoEn IS NULL) ORDER BY a.creadoEn, a.id")
    List<Aviso> delLocal(@Param("localId") Long localId, @Param("pendientes") boolean pendientes);
}
