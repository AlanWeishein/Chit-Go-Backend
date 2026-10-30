package com.chitgo.pedidos.infraestructura.persistencia;

import org.springframework.stereotype.Repository;

import com.chitgo.pedidos.dominio.Ticket;
import com.chitgo.pedidos.dominio.puertos.TicketRepository;

@Repository
class TicketRepositoryAdapter implements TicketRepository {

    private final TicketJpaRepository jpa;

    TicketRepositoryAdapter(TicketJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Ticket guardar(Ticket ticket) {
        return jpa.saveAndFlush(ticket);
    }
}
