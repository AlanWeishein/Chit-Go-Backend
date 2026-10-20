package com.chitgo.pedidos.dominio.puertos;

import com.chitgo.pedidos.dominio.EventoTiempoReal;

/** Salida de tiempo real. La implementa el adaptador de STOMP (PublicadorStomp). */
public interface PublicadorDeEventos {

    void aCaja(Long localId, EventoTiempoReal evento);

    void aCocina(Long localId, EventoTiempoReal evento);

    void aMozos(Long localId, EventoTiempoReal evento);

    void aAlertas(Long localId, EventoTiempoReal evento);

    void aPantalla(Long localId, EventoTiempoReal evento);

    void aCliente(String tokenSesion, EventoTiempoReal evento);
}
