package com.chitgo.pedidos.dominio.puertos;

import java.util.Optional;

import com.chitgo.pedidos.dominio.Ticket;

public interface TicketRepository {

    /** Guarda y fuerza el INSERT ya. Hay un solo ticket por cuenta (UNIQUE en cuenta_id). */
    Ticket guardar(Ticket ticket);

    /** El ticket de la cuenta, si ya se cerró. */
    Optional<Ticket> buscarDeLaCuenta(Long cuentaId);
}
