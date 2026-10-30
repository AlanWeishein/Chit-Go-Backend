package com.chitgo.pedidos.dominio;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;

/**
 * La cola de cocina es una cola real, no un ORDER BY (pedido del profesor). Primero los de prioridad manual
 * y, dentro de cada grupo, el que llegó antes. La prioridad la pone siempre una persona: acá nunca se decide
 * por precio ni por nada automático. "Llegada" es el momento en que Caja lo resolvió (resueltoEn), o sea
 * cuando entró a la cocina; si empatan, el de id menor.
 */
public final class ColaDeCocina {

    private static final Comparator<Pedido> ORDEN = Comparator
            .comparing((Pedido p) -> !p.isPrioridadManual())
            .thenComparing(Pedido::getResueltoEn, Comparator.nullsLast(Comparator.<Instant>naturalOrder()))
            .thenComparing(Pedido::getId, Comparator.nullsLast(Comparator.<Long>naturalOrder()));

    private final PriorityQueue<Pedido> cola = new PriorityQueue<>(ORDEN);

    public static ColaDeCocina con(Collection<Pedido> aprobados) {
        ColaDeCocina nueva = new ColaDeCocina();
        aprobados.forEach(nueva::encolar);
        return nueva;
    }

    public void encolar(Pedido pedido) {
        cola.add(pedido);
    }

    /** El próximo que le toca a Cocina, sin sacarlo. */
    public Optional<Pedido> siguiente() {
        return Optional.ofNullable(cola.peek());
    }

    /** Todos, en el orden en que hay que prepararlos. */
    public List<Pedido> enOrden() {
        PriorityQueue<Pedido> copia = new PriorityQueue<>(cola);
        List<Pedido> resultado = new ArrayList<>();
        while (!copia.isEmpty()) {
            resultado.add(copia.poll());
        }
        return resultado;
    }
}
