package com.chitgo.usuarios.infraestructura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chitgo.usuarios.dominio.Usuario;

interface UsuarioJpaRepository extends JpaRepository<Usuario, Long> {
}
