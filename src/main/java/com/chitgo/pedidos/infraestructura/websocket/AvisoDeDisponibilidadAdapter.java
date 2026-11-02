package com.chitgo.pedidos.infraestructura.websocket;

import org.springframework.stereotype.Component;

import com.chitgo.compartido.dominio.Reloj;
import com.chitgo.menu.dominio.puertos.AvisoDeDisponibilidad;
import com.chitgo.pedidos.dominio.EventoTiempoReal;
import com.chitgo.pedidos.dominio.TipoEvento;
import com.chitgo.pedidos.dominio.puertos.ClienteSesionRepository;
import com.chitgo.pedidos.dominio.puertos.PublicadorDeEventos;

/**
 * El puerto de menu para el aviso de HU-09: un PLATO_BAJA o un PLATO_DISPONIBLE a cada sesión de las cuentas no
 * cerradas del local, por su cola (decidido el 29/10). Al recibirlo, el celular vuelve a pedir el menú.
 */
@Component
class AvisoDeDisponibilidadAdapter implements AvisoDeDisponibilidad {

    private final ClienteSesionRepository sesiones;
    private final PublicadorDeEventos eventos;
    private final Reloj reloj;

    AvisoDeDisponibilidadAdapter(ClienteSesionRepository sesiones, PublicadorDeEventos eventos, Reloj reloj) {
        this.sesiones = sesiones;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    @Override
    public void platoCambio(Long localId, Long platoId, boolean disponible) {
        EventoTiempoReal evento = EventoTiempoReal.dePlato(
                disponible ? TipoEvento.PLATO_DISPONIBLE : TipoEvento.PLATO_BAJA, platoId, reloj.ahora());
        for (String token : sesiones.tokensAbiertosDelLocal(localId)) {
            eventos.aCliente(token, evento);
        }
    }
}
