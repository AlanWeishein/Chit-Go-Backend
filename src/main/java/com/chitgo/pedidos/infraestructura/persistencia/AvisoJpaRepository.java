package com.chitgo.pedidos.infraestructura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chitgo.pedidos.dominio.Aviso;

interface AvisoJpaRepository extends JpaRepository<Aviso, Long> {
}
