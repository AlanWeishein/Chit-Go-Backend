package com.chitgo.pedidos.infraestructura.persistencia;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chitgo.pedidos.dominio.Mesa;

interface MesaJpaRepository extends JpaRepository<Mesa, Long> {

    Optional<Mesa> findByTokenQr(String tokenQr);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Mesa m WHERE m.tokenQr = :tokenQr")
    Optional<Mesa> buscarParaAbrirCuenta(@Param("tokenQr") String tokenQr);
}
