package com.chitgo.pedidos.infraestructura.persistencia;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chitgo.pedidos.dominio.ClienteSesion;

interface ClienteSesionJpaRepository extends JpaRepository<ClienteSesion, Long> {

    Optional<ClienteSesion> findByTokenSesion(String tokenSesion);
}
