package com.chitgo.pedidos.infraestructura.persistencia;

import java.util.Optional;
import java.util.List;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chitgo.pedidos.dominio.Mesa;

interface MesaJpaRepository extends JpaRepository<Mesa, Long> {

    Optional<Mesa> findByTokenQrAndActivaTrue(String tokenQr);

    List<Mesa> findByLocalIdAndActivaTrueOrderByNumeroAscIdAsc(Long localId);

    Page<Mesa> findByLocalIdAndActivaTrue(Long localId, Pageable pageable);

    Optional<Mesa> findByIdAndLocalId(Long id, Long localId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Mesa m WHERE m.id = :id AND m.localId = :localId")
    Optional<Mesa> paraGestionar(@Param("id") Long id, @Param("localId") Long localId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Mesa m WHERE m.tokenQr = :tokenQr AND m.activa = TRUE")
    Optional<Mesa> buscarParaAbrirCuenta(@Param("tokenQr") String tokenQr);
}
