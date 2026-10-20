package com.chitgo.pedidos.infraestructura.persistencia;

import org.springframework.stereotype.Repository;

import com.chitgo.pedidos.dominio.Aviso;
import com.chitgo.pedidos.dominio.puertos.AvisoRepository;

@Repository
class AvisoRepositoryAdapter implements AvisoRepository {

    private final AvisoJpaRepository jpa;

    AvisoRepositoryAdapter(AvisoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Aviso guardar(Aviso aviso) {
        return jpa.save(aviso);
    }
}
