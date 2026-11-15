package com.chitgo.pedidos.infraestructura.programacion;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.chitgo.compartido.aplicacion.ReglasDelNegocio;
import com.chitgo.pedidos.aplicacion.MesasSinMozoService;

/**
 * HU-14 (CP-07-05): una vez por minuto, en el segundo 0 de la hora del local, avisa las mesas que se quedan sin
 * mozo en los locales donde empieza un turno. La lógica está en MesasSinMozoService; acá solo se agenda. Corre en
 * el scheduler propio de ProgramacionConfig. Si una pasada tira una excepción, Spring la deja en el log y la tarea
 * sigue agendada. Se apaga con {@code chitgo.alertas.mesa-sin-mozo.habilitada=false}, como en los tests.
 */
@Component
@ConditionalOnProperty(name = "chitgo.alertas.mesa-sin-mozo.habilitada", havingValue = "true", matchIfMissing = true)
public class AlertaDeMesaSinMozoProgramada {

    /**
     * La de MozoDeTurnoAdapter: el turno empieza a una hora de pared del local. El {@code zone} tiene que ser una
     * constante, así que se lee la propiedad con un placeholder, que Spring resuelve al agendar la tarea, y el valor
     * por defecto es el mismo de ReglasDelNegocio.
     */
    static final String ZONA = "${chitgo.reglas.zona-horaria:" + ReglasDelNegocio.ZONA_POR_DEFECTO + "}";

    private final MesasSinMozoService mesasSinMozo;

    public AlertaDeMesaSinMozoProgramada(MesasSinMozoService mesasSinMozo) {
        this.mesasSinMozo = mesasSinMozo;
    }

    @Scheduled(cron = "0 * * * * *", zone = ZONA)
    public void revisar() {
        mesasSinMozo.avisarAlEmpezarLosTurnos();
    }
}
