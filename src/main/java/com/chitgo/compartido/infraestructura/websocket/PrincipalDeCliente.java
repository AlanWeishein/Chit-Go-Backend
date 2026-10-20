package com.chitgo.compartido.infraestructura.websocket;

import java.security.Principal;

/**
 * El cliente anónimo conectado por STOMP. El nombre es su tokenSesion: así lo que se manda a
 * /user/queue/pedido le llega solo a su celular.
 */
public record PrincipalDeCliente(String name, Long localId) implements Principal {

    @Override
    public String getName() {
        return name;
    }
}
