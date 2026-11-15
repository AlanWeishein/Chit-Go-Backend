package com.chitgo.usuarios.infraestructura.persistencia;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import org.springframework.stereotype.Repository;

import com.chitgo.compartido.aplicacion.ReglasDelNegocio;
import com.chitgo.usuarios.dominio.DiaSemana;
import com.chitgo.usuarios.dominio.puertos.TurnosQueEmpiezan;

/** Los turnos son horas de pared del local ({@code chitgo.reglas.zona-horaria}), no UTC. */
@Repository
class TurnosQueEmpiezanAdapter implements TurnosQueEmpiezan {

    private final EmpleadoJpaRepository jpa;
    private final ReglasDelNegocio reglas;

    TurnosQueEmpiezanAdapter(EmpleadoJpaRepository jpa, ReglasDelNegocio reglas) {
        this.jpa = jpa;
        this.reglas = reglas;
    }

    /**
     * Todo el minuto, de :00 a :59: el turno se guarda como TIME y se puede cargar con segundos. Como el último
     * segundo es el 59 del mismo minuto, el turno de las 23:59 no salta al día siguiente.
     */
    @Override
    public Set<Long> localesConTurnoQueEmpieza(Instant ahora) {
        ZonedDateTime local = ahora.atZone(reglas.zonaHoraria());
        LocalTime desde = local.toLocalTime().truncatedTo(ChronoUnit.MINUTES);
        DiaSemana dia = DiaSemana.values()[local.getDayOfWeek().getValue() - 1];
        return jpa.localesConTurnoQueEmpieza(dia, desde, desde.plusSeconds(59));
    }
}
