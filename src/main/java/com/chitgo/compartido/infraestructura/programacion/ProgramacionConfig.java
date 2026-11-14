package com.chitgo.compartido.infraestructura.programacion;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Prende los {@code @Scheduled} de toda la app. Sin esto ninguna tarea programada corre y los tests no se
 * enteran, porque llaman a los services a mano (ST-10). Cada tarea se apaga con su propia propiedad.
 *
 * <p>Las tareas corren en un scheduler propio, con hilos {@code programadas-*}. Sin él, {@code @EnableScheduling}
 * usa el único TaskScheduler que encuentra, que es el del broker de STOMP, y la alerta de demora corría en los
 * hilos {@code MessageBroker-*} del WebSocket (punto 17 del 10/11). Se le pasa con SchedulingConfigurer, a la
 * vista, y no con un bean llamado {@code taskScheduler}, que depende de una regla de Spring que no se ve acá.
 */
@Configuration
@EnableScheduling
public class ProgramacionConfig implements SchedulingConfigurer {

    static final String PREFIJO_DE_LOS_HILOS = "programadas-";

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.setTaskScheduler(tareasProgramadas());
    }

    /**
     * Dos hilos, así una tarea larga no atrasa a la otra (la alerta de demora cada 5 segundos y la del inicio de
     * los turnos, cada minuto). Como bean, Spring lo arranca y lo apaga con el contexto.
     */
    @Bean("tareasProgramadas")
    ThreadPoolTaskScheduler tareasProgramadas() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix(PREFIJO_DE_LOS_HILOS);
        return scheduler;
    }
}
