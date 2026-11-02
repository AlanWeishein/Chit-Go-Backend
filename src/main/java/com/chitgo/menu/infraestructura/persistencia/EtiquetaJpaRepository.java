package com.chitgo.menu.infraestructura.persistencia;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chitgo.menu.dominio.Etiqueta;

interface EtiquetaJpaRepository extends JpaRepository<Etiqueta, Long> {

    List<Etiqueta> findByLocalIdAndIdIn(Long localId, Collection<Long> ids);
}
