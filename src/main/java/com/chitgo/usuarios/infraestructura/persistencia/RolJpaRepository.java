package com.chitgo.usuarios.infraestructura.persistencia;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.chitgo.usuarios.dominio.Rol;

interface RolJpaRepository extends JpaRepository<Rol, Long> {

    @Query("SELECT DISTINCT r FROM Rol r LEFT JOIN FETCH r.permisos")
    List<Rol> buscarTodosConPermisos();
}
