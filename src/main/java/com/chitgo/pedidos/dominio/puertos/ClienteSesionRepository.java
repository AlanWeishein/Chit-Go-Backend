package com.chitgo.pedidos.dominio.puertos;

import java.util.Optional;

import com.chitgo.pedidos.dominio.ClienteSesion;

public interface ClienteSesionRepository {

    Optional<ClienteSesion> buscarPorToken(String tokenSesion);

    ClienteSesion guardar(ClienteSesion sesion);
}
