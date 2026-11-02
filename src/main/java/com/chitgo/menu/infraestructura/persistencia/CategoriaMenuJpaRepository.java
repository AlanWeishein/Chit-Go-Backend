package com.chitgo.menu.infraestructura.persistencia;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chitgo.menu.dominio.CategoriaMenu;

interface CategoriaMenuJpaRepository extends JpaRepository<CategoriaMenu, Long> {

    Optional<CategoriaMenu> findByIdAndLocalId(Long id, Long localId);

    List<CategoriaMenu> findByLocalIdOrderByOrdenAscIdAsc(Long localId);
}
