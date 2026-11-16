package com.chitgo.usuarios.infraestructura.persistencia;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chitgo.usuarios.dominio.Local;

interface LocalJpaRepository extends JpaRepository<Local, Long> {

    Optional<Local> findByTokenStaff(String tokenStaff);
}
