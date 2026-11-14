package com.chitgo.usuarios.infraestructura.persistencia;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import org.springframework.stereotype.Repository;

import com.chitgo.usuarios.dominio.DiaSemana;
import com.chitgo.usuarios.dominio.puertos.TurnosQueEmpiezan;

@Repository
class TurnosQueEmpiezanAdapter implements TurnosQueEmpiezan {

    /** Los turnos son horas de pared del local, no UTC. */
    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    private final EmpleadoJpaRepository jpa;

    TurnosQueEmpiezanAdapter(EmpleadoJpaRepository jpa) {
        this.jpa = jpa;
    }

    /**
     * Todo el minuto, de :00 a :59: el turno se guarda como TIME y se puede cargar con segundos. Como el último
     * segundo es el 59 del mismo minuto, el turno de las 23:59 no salta al día siguiente.
     */
    @Override
    public Set<Long> localesConTurnoQueEmpieza(Instant ahora) {
        ZonedDateTime local = ahora.atZone(ZONA);
        LocalTime desde = local.toLocalTime().truncatedTo(ChronoUnit.MINUTES);
        DiaSemana dia = DiaSemana.values()[local.getDayOfWeek().getValue() - 1];
        return jpa.localesConTurnoQueEmpieza(dia, desde, desde.plusSeconds(59));
    }
}
