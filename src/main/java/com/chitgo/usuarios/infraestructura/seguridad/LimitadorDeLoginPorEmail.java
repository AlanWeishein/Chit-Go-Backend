package com.chitgo.usuarios.infraestructura.seguridad;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;

import org.springframework.stereotype.Component;

import com.chitgo.compartido.aplicacion.ReglasDelNegocio;
import com.chitgo.compartido.dominio.Reloj;

/**
 * Ventana móvil por origen y email del único endpoint de login por email.
 * La reserva es atómica; no hay cupo compartido de solicitudes por IP.
 */
@Component
public class LimitadorDeLoginPorEmail {

    private final PropiedadesDeLimiteDeLogin limites;
    private final Reloj reloj;
    // Orden de última admisión: permite descartar claves vencidas desde el principio sin barrer todo.
    private final LinkedHashMap<Clave, ArrayDeque<Instant>> ventanas = new LinkedHashMap<>();
    private Instant ultimaHora = Instant.MIN;

    public LimitadorDeLoginPorEmail(PropiedadesDeLimiteDeLogin limites, Reloj reloj, ReglasDelNegocio reglas) {
        if (limites.solicitudes() >= reglas.intentosParaBloquear()) {
            throw new IllegalArgumentException("El cupo del login debe ser menor que intentos-para-bloquear");
        }
        this.limites = limites;
        this.reloj = reloj;
    }

    public synchronized void verificar(String origen, String claveDeEmail) {
        Instant ahora = reloj.ahora();
        // Un ajuste del reloj hacia atrás no libera cupos ni desordena la expiración.
        if (ahora.isBefore(ultimaHora)) ahora = ultimaHora;
        ultimaHora = ahora;
        Instant corte = ahora.minus(limites.ventana());
        while (!ventanas.isEmpty() && !ventanas.firstEntry().getValue().getLast().isAfter(corte)) {
            ventanas.pollFirstEntry();
        }

        Clave clave = new Clave(origen, claveDeEmail);
        ArrayDeque<Instant> intentos = ventanas.get(clave);
        if (intentos == null) {
            if (ventanas.size() >= limites.maximoDeClaves()) {
                // No expulsar una clave activa: permitiría reiniciar su cupo llenando la memoria.
                Instant liberaUnaClave = ventanas.firstEntry().getValue().getLast().plus(limites.ventana());
                throw new LimiteDeLoginExcedidoException(Duration.between(ahora, liberaUnaClave));
            }
            intentos = new ArrayDeque<>();
        }
        while (!intentos.isEmpty() && !intentos.getFirst().isAfter(corte)) intentos.removeFirst();
        if (intentos.size() >= limites.solicitudes()) {
            Instant liberaUnIntento = intentos.getFirst().plus(limites.ventana());
            throw new LimiteDeLoginExcedidoException(Duration.between(ahora, liberaUnIntento));
        }
        intentos.addLast(ahora);
        ventanas.remove(clave);
        ventanas.put(clave, intentos);
    }

    private record Clave(String origen, String email) { }
}
