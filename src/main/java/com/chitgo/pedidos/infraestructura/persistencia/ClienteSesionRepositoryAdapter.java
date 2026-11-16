package com.chitgo.pedidos.infraestructura.persistencia;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.chitgo.pedidos.dominio.ClienteSesion;
import com.chitgo.pedidos.dominio.EstadoCuenta;
import com.chitgo.pedidos.dominio.puertos.ClienteSesionRepository;

@Repository
class ClienteSesionRepositoryAdapter implements ClienteSesionRepository {

    private final ClienteSesionJpaRepository jpa;

    ClienteSesionRepositoryAdapter(ClienteSesionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<ClienteSesion> buscarPorToken(String tokenSesion) {
        return jpa.findByTokenSesion(tokenSesion);
    }

    @Override
    public ClienteSesion guardar(ClienteSesion sesion) {
        return jpa.saveAndFlush(sesion);
    }

    @Override
    public List<String> tokensAbiertosDelLocal(Long localId) {
        return jpa.tokensDelLocalSinCuentaEn(localId, EstadoCuenta.CERRADA);
    }
}
