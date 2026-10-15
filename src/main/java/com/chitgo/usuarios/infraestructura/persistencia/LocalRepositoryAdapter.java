package com.chitgo.usuarios.infraestructura.persistencia;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.chitgo.usuarios.dominio.Local;
import com.chitgo.usuarios.dominio.puertos.LocalRepository;

@Repository
class LocalRepositoryAdapter implements LocalRepository {

    private final LocalJpaRepository jpa;

    LocalRepositoryAdapter(LocalJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Local> buscarPorId(Long id) {
        return jpa.findById(id);
    }
}
