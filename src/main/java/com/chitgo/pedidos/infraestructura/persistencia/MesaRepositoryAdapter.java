package com.chitgo.pedidos.infraestructura.persistencia;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.chitgo.pedidos.dominio.Mesa;
import com.chitgo.pedidos.dominio.puertos.MesaRepository;

@Repository
class MesaRepositoryAdapter implements MesaRepository {

    private final MesaJpaRepository jpa;

    MesaRepositoryAdapter(MesaJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Mesa> buscarPorTokenQr(String tokenQr) {
        return jpa.findByTokenQr(tokenQr);
    }

    @Override
    public Optional<Mesa> buscarPorTokenQrParaAbrirCuenta(String tokenQr) {
        return jpa.buscarParaAbrirCuenta(tokenQr);
    }
}
