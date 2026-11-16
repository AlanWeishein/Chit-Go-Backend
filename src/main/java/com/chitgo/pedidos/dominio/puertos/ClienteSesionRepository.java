package com.chitgo.pedidos.dominio.puertos;

import java.util.List;
import java.util.Optional;

import com.chitgo.pedidos.dominio.ClienteSesion;

public interface ClienteSesionRepository {

    Optional<ClienteSesion> buscarPorToken(String tokenSesion);

    ClienteSesion guardar(ClienteSesion sesion);

    /** Los tokens de las sesiones de las cuentas no cerradas del local: los celulares que siguen en una mesa. */
    List<String> tokensAbiertosDelLocal(Long localId);
}
