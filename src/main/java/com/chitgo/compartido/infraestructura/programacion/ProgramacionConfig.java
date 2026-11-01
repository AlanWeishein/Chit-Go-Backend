package com.chitgo.compartido.infraestructura.programacion;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Prende los {@code @Scheduled} de toda la app. Sin esto ninguna tarea programada corre y los tests no se
 * enteran, porque llaman a los services a mano (ST-10). Cada tarea se apaga con su propia propiedad.
 */
@Configuration
@EnableScheduling
public class ProgramacionConfig {
}
