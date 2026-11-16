package com.chitgo.usuarios.infraestructura.persistencia;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.chitgo.usuarios.dominio.Rol;
import com.chitgo.usuarios.dominio.puertos.RolRepository;

@Repository
class RolRepositoryAdapter implements RolRepository {

    private final RolJpaRepository jpa;

    RolRepositoryAdapter(RolJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Rol> buscarPorCodigo(String codigo) {
        return jpa.findByCodigo(codigo);
    }
}
