package com.chitgo.compartido.infraestructura.tiempo;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.chitgo.compartido.dominio.Reloj;

/** La hora de la máquina donde corre el back. */
@Component
public class RelojDelSistema implements Reloj {

    @Override
    public Instant ahora() {
        return Instant.now();
    }
}
