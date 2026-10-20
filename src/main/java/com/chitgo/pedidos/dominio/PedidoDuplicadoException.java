package com.chitgo.pedidos.dominio;

/** Ya existía un pedido con ese uuidCliente: un reintento o un doble toque que llegaron a la vez (RNF-09). */
public class PedidoDuplicadoException extends RuntimeException {

    public PedidoDuplicadoException() {
        super("Ya existe un pedido con ese identificador");
    }
}
