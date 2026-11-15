package com.chitgo.pedidos.infraestructura.programacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import com.chitgo.pedidos.aplicacion.MesasSinMozoService;

/**
 * El cableado de la alerta de mesa sin mozo al empezar los turnos (ST-23, tarea 2), con la propiedad prendida como
 * en la demo. Como AlertaDeDemoraProgramadaTest: el service es un mock, así una pasada no toca nada, y el contexto
 * se cierra al terminar. Que con la propiedad apagada no se agenda lo prueba MesaSinMozoTest. Con otra zona en
 * chitgo.reglas.zona-horaria (ST-23, tarea 4), el cron se resuelve en esa.
 */
@SpringBootTest(properties = {
        "chitgo.alertas.mesa-sin-mozo.habilitada=true",
        "chitgo.reglas.zona-horaria=America/Montevideo"})
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
                .isInstanceOfSatisfying(CronTask.class, tarea -> {
                    assertThat(tarea.getExpression()).isEqualTo("0 * * * * *");
                    // CronTrigger no tiene un getter de la zona.
                    assertThat(ReflectionTestUtils.getField(tarea.getTrigger(), "zoneId"))
                            .isEqualTo(ZoneId.of("America/Montevideo"));
                });
    }
}
