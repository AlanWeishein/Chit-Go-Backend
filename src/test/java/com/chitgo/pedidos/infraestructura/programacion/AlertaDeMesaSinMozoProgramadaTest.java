package com.chitgo.pedidos.infraestructura.programacion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.chitgo.pedidos.aplicacion.MesasSinMozoService;

/**
 * El cableado de la alerta de mesa sin mozo al empezar los turnos (ST-23, tarea 2), con la propiedad prendida como
 * en la demo. Como AlertaDeDemoraProgramadaTest: el service es un mock, así una pasada no toca nada, y el contexto
 * se cierra al terminar. Que con la propiedad apagada no se agenda lo prueba MesaSinMozoTest.
 */
@SpringBootTest(properties = "chitgo.alertas.mesa-sin-mozo.habilitada=true")
@ActiveProfiles("dev")
@DirtiesContext
class AlertaDeMesaSinMozoProgramadaTest {

    /** Como lo nombra ScheduledMethodRunnable: la clase y el método. */
    static final String TAREA = AlertaDeMesaSinMozoProgramada.class.getName() + ".revisar";

    @MockitoBean
    private MesasSinMozoService mesasSinMozo;

    @Autowired
    private ScheduledTaskHolder tareas;

    @Test
    void conLaPropiedadPrendidaQuedaAgendadaEnElSegundoCeroDeCadaMinuto() {
        assertThat(tareas.getScheduledTasks())
                .map(ScheduledTask::getTask)
                .filteredOn(tarea -> tarea.toString().equals(TAREA))
                .singleElement()
                .isInstanceOfSatisfying(CronTask.class, tarea ->
                        assertThat(tarea.getExpression()).isEqualTo("0 * * * * *"));
    }
}
