package com.chitgo.pedidos.infraestructura.persistencia;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chitgo.pedidos.dominio.ClienteSesion;
import com.chitgo.pedidos.dominio.EstadoCuenta;

interface ClienteSesionJpaRepository extends JpaRepository<ClienteSesion, Long> {

    Optional<ClienteSesion> findByTokenSesion(String tokenSesion);

    @Query("SELECT s.tokenSesion FROM ClienteSesion s JOIN s.cuenta c "
            + "WHERE s.localId = :localId AND c.estado <> :cerrada ORDER BY s.id")
    List<String> tokensDelLocalSinCuentaEn(@Param("localId") Long localId, @Param("cerrada") EstadoCuenta cerrada);
}
