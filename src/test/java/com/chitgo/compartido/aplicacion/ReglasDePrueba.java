package com.chitgo.compartido.aplicacion;

import java.time.Duration;
import java.time.ZoneId;

/** Para los tests que arman los services a mano, sin Spring. */
public final class ReglasDePrueba {

    /**
     * Los valores de hoy, los mismos que los {@code @DefaultValue} de ReglasDelNegocio y el application.yml.
     * ReglasDelNegocioTest controla que no se separen.
     */
    public static final ReglasDelNegocio DE_HOY = new ReglasDelNegocio(Duration.ofSeconds(180), Duration.ofHours(1),
            Duration.ofMinutes(10), Duration.ofDays(7), 5, ZoneId.of("America/Argentina/Buenos_Aires"));

    private ReglasDePrueba() {
    }
}
