package com.chitgo.pedidos.infraestructura.programacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.chitgo.pedidos.aplicacion.AlertaDeDemoraService;

/**
 * El cableado de la tarea de HU-08, con la propiedad prendida como en la demo. Si faltara {@code @EnableScheduling},
 * los tests que llaman a revisarDemorados() a mano darían verde igual y la alerta no saldría nunca (ST-10). Acá no
 * se espera ninguna pasada: el service es un mock por si alguna corre, y el contexto se cierra al terminar para que
 * la tarea no siga agendada durante el resto de la suite.
 *
 * <p>Que con la propiedad apagada no se agenda lo prueba AlertaDeDemoraTest, en el contexto de la suite.
 */
@SpringBootTest(properties = "chitgo.alertas.demora.habilitada=true")
@ActiveProfiles("dev")
@DirtiesContext
class AlertaDeDemoraProgramadaTest {

    /** Como lo nombra ScheduledMethodRunnable: la clase y el método. */
    static final String TAREA = AlertaDeDemoraProgramada.class.getName() + ".revisar";

    @MockitoBean
    private AlertaDeDemoraService alertaDeDemora;

    @Autowired
    private ScheduledTaskHolder tareas;

    @Test
    void conLaPropiedadPrendidaQuedaAgendadaCadaCincoSegundos() {
        assertThat(tareas.getScheduledTasks())
                .map(ScheduledTask::getTask)
                .filteredOn(tarea -> tarea.toString().equals(TAREA))
                .singleElement()
                .isInstanceOfSatisfying(FixedDelayTask.class, tarea ->
                        assertThat(tarea.getIntervalDuration()).isEqualTo(Duration.ofSeconds(5)));
    }
}
