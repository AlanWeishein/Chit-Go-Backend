package com.chitgo.usuarios.infraestructura.persistencia;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.chitgo.usuarios.dominio.DiaSemana;
import com.chitgo.usuarios.dominio.puertos.MozoDeTurno;

@Repository
class MozoDeTurnoAdapter implements MozoDeTurno {

    /** Los turnos son horas de pared del local, no UTC. */
    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    private final EmpleadoJpaRepository jpa;

    MozoDeTurnoAdapter(EmpleadoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Long> buscarParaMesa(Long mesaId, Instant ahora) {
        ZonedDateTime local = ahora.atZone(ZONA);
        // DiaSemana va de LUN a DOM, igual que DayOfWeek menos uno.
        DiaSemana dia = DiaSemana.values()[local.getDayOfWeek().getValue() - 1];
        return jpa.mozosDeTurno(mesaId, dia, local.toLocalTime(), local.toLocalDate()).stream().findFirst();
    }
}
