package com.chitgo.pedidos.infraestructura.programacion;

import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.chitgo.pedidos.aplicacion.AlertaDeDemoraService;

/**
 * HU-08: corre la revisión de los pedidos demorados cada 5 segundos. La lógica está en AlertaDeDemoraService;
 * acá solo se agenda. Con {@code fixedDelay} una pasada nunca se pisa con la siguiente, que es lo que supone
 * el service. Si una pasada tira una excepción, Spring la deja en el log y la tarea sigue agendada.
 * Se apaga con {@code chitgo.alertas.demora.habilitada=false}, como en los tests.
 */
@Component
@ConditionalOnProperty(name = "chitgo.alertas.demora.habilitada", havingValue = "true", matchIfMissing = true)
public class AlertaDeDemoraProgramada {

    private final AlertaDeDemoraService alertaDeDemora;

    public AlertaDeDemoraProgramada(AlertaDeDemoraService alertaDeDemora) {
        this.alertaDeDemora = alertaDeDemora;
    }

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
    public void revisar() {
        alertaDeDemora.revisarDemorados();
    }
}
