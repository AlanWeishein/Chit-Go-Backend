package com.chitgo.usuarios.infraestructura.seguridad;

import java.time.Duration;

public class LimiteDeLoginExcedidoException extends RuntimeException {

    private final long segundosDeEspera;

    public LimiteDeLoginExcedidoException(Duration espera) {
        super("Demasiadas solicitudes de ingreso. Intentá de nuevo más tarde.");
        segundosDeEspera = Math.max(1, espera.getSeconds() + (espera.getNano() == 0 ? 0 : 1));
    }

    public long segundosDeEspera() {
        return segundosDeEspera;
    }
}
