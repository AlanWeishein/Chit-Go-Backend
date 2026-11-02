package com.chitgo.pedidos.aplicacion;

import static com.chitgo.pruebas.Entidades.con;
import static com.chitgo.pruebas.Entidades.conId;
import java.time.Instant;
import java.util.UUID;
import com.chitgo.pedidos.dominio.*;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

final class DatosSprint2 {
    static final Instant AHORA = Instant.parse("2026-11-01T20:00:00Z");
    static final UsuarioAutenticado JUAN = new UsuarioAutenticado(30L, "MOZO", 1L, false, AHORA.plusSeconds(600));
    static final UsuarioAutenticado CARLA = new UsuarioAutenticado(40L, "CAJA", 1L, false, AHORA.plusSeconds(3600));

    static Mesa mesa(long id) {
        return conId(new Mesa(1L, Long.toString(id), UUID.randomUUID().toString()), id);
    }

    static Cuenta cuenta(long id, Mesa mesa) {
        return con(conId(new Cuenta(1L, mesa, 3L, AHORA.minusSeconds(600)), id), "version", 0);
    }

    static ClienteSesion cliente(Cuenta cuenta, boolean iniciador) {
        return conId(new ClienteSesion(1L, cuenta, "cliente", iniciador), 100L);
    }

    static Pedido pedido(long id, Cuenta cuenta, EstadoPedido estado) {
        Pedido pedido = con(conId(new Pedido(1L, cuenta, cliente(cuenta, true), UUID.randomUUID().toString(), AHORA), id),
                "estado", estado);
        return con(pedido, "version", 0);
    }

    static Aviso aviso(long id, Cuenta cuenta, TipoAviso tipo) {
        return conId(new Aviso(cuenta, tipo, AHORA), id);
    }
}
