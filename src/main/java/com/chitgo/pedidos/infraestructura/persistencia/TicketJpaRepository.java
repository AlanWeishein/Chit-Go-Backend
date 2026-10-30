package com.chitgo.pedidos.infraestructura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chitgo.pedidos.dominio.Ticket;

interface TicketJpaRepository extends JpaRepository<Ticket, Long> {
}
