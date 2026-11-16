package com.chitgo.usuarios.infraestructura.persistencia;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.chitgo.compartido.aplicacion.ReglasDelNegocio;
import com.chitgo.usuarios.dominio.DiaSemana;
import com.chitgo.usuarios.dominio.puertos.MozoDeTurno;

@Repository
class MozoDeTurnoAdapter implements MozoDeTurno {

    private final EmpleadoJpaRepository jpa;
    private final ReglasDelNegocio reglas;

    MozoDeTurnoAdapter(EmpleadoJpaRepository jpa, ReglasDelNegocio reglas) {
        this.jpa = jpa;
        this.reglas = reglas;
    }

    /** Los turnos son horas de pared del local ({@code chitgo.reglas.zona-horaria}), no UTC. */
    @Override
    public Optional<Long> buscarParaMesa(Long mesaId, Instant ahora) {
        ZonedDateTime local = ahora.atZone(reglas.zonaHoraria());
        LocalDate hoy = local.toLocalDate();
        // Ayer sale de la fecha, así el día anterior del lunes es el domingo.
        LocalDate ayer = hoy.minusDays(1);
        return jpa.mozosDeTurno(mesaId, dia(hoy), local.toLocalTime(), hoy, dia(ayer), ayer).stream().findFirst();
    }

    /** DiaSemana va de LUN a DOM, igual que DayOfWeek menos uno. */
    private static DiaSemana dia(LocalDate fecha) {
        return DiaSemana.values()[fecha.getDayOfWeek().getValue() - 1];
    }
}
