package com.chitgo.usuarios.infraestructura.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.chitgo.compartido.aplicacion.ReglasDePrueba;
import com.chitgo.compartido.dominio.RelojDePrueba;

class LimitadorDeLoginPorEmailTest {
    private final RelojDePrueba reloj = new RelojDePrueba(Instant.parse("2026-11-16T15:00:00Z"));
    private final LimitadorDeLoginPorEmail limite = nuevo(10_000);

    private LimitadorDeLoginPorEmail nuevo(int claves) {
        return new LimitadorDeLoginPorEmail(new PropiedadesDeLimiteDeLogin(3, Duration.ofSeconds(60), claves),
                reloj, ReglasDePrueba.DE_HOY);
    }

    private void agotar(LimitadorDeLoginPorEmail limitador, String origen, String email) {
        for (int i = 0; i < 3; i++) limitador.verificar(origen, email);
    }

    @Test
    void laCuartaSolicitudSeRechazaConEsperaCalculada() {
        agotar(limite, "origen", "email");
        assertThat(assertThrows(LimiteDeLoginExcedidoException.class,
                () -> limite.verificar("origen", "email")).segundosDeEspera()).isEqualTo(60);
    }

    @Test
    void laVentanaEsMovilYElBordeExactoLiberaSoloUnLugar() {
        limite.verificar("origen", "email");
        reloj.avanzar(Duration.ofSeconds(20));
        limite.verificar("origen", "email");
        reloj.avanzar(Duration.ofSeconds(20));
        limite.verificar("origen", "email");
        reloj.avanzar(Duration.ofMillis(19_500));
        assertThat(assertThrows(LimiteDeLoginExcedidoException.class,
                () -> limite.verificar("origen", "email")).segundosDeEspera()).isEqualTo(1);
        reloj.avanzar(Duration.ofMillis(500));
        limite.verificar("origen", "email");
        assertThat(assertThrows(LimiteDeLoginExcedidoException.class,
                () -> limite.verificar("origen", "email")).segundosDeEspera()).isEqualTo(20);
    }

    @Test
    void rechazarNoExtiendeElBloqueoTemporal() {
        agotar(limite, "origen", "email");
        reloj.avanzar(Duration.ofSeconds(59));
        for (int i = 0; i < 10; i++) assertThrows(LimiteDeLoginExcedidoException.class,
                () -> limite.verificar("origen", "email"));
        reloj.avanzar(Duration.ofSeconds(1));
        agotar(limite, "origen", "email");
    }

    @Test
    void niOtraCuentaNiOtroOrigenCompartenElCupo() {
        agotar(limite, "origen", "email");
        agotar(limite, "otro-origen", "email");
        for (int i = 0; i < 100; i++) agotar(limite, "origen", "cuenta-" + i);
    }

    @Test
    void laMemoriaLlenaRechazaSinExpulsarUnaClaveActiva() {
        LimitadorDeLoginPorEmail pequeno = nuevo(2);
        agotar(pequeno, "origen", "a");
        reloj.avanzar(Duration.ofSeconds(10));
        pequeno.verificar("origen", "b");
        assertThat(assertThrows(LimiteDeLoginExcedidoException.class,
                () -> pequeno.verificar("otro-origen", "c")).segundosDeEspera()).isEqualTo(50);
        assertThrows(LimiteDeLoginExcedidoException.class, () -> pequeno.verificar("origen", "a"));
        // Una clave ya reservada conserva sus lugares aunque no quepan claves nuevas.
        pequeno.verificar("origen", "b");
        reloj.avanzar(Duration.ofSeconds(50));
        pequeno.verificar("otro-origen", "c");
    }

    @Test
    void actualizarUnaClaveNoExpiraLasOtrasAntesDeTiempo() {
        LimitadorDeLoginPorEmail pequeno = nuevo(2);
        pequeno.verificar("origen", "a");
        reloj.avanzar(Duration.ofSeconds(10));
        pequeno.verificar("origen", "b");
        reloj.avanzar(Duration.ofSeconds(10));
        pequeno.verificar("origen", "a");
        reloj.avanzar(Duration.ofSeconds(40));
        assertThat(assertThrows(LimiteDeLoginExcedidoException.class,
                () -> pequeno.verificar("origen", "c")).segundosDeEspera()).isEqualTo(10);
        reloj.avanzar(Duration.ofSeconds(10));
        pequeno.verificar("origen", "c");
    }

    @Test
    void cienSolicitudesConcurrentesSoloReservanTresLugares() throws Exception {
        try (var executor = Executors.newFixedThreadPool(16)) {
            CountDownLatch inicio = new CountDownLatch(1);
            var resultados = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 100; i++) resultados.add(executor.submit(() -> {
                inicio.await();
                try {
                    limite.verificar("origen", "email");
                    return true;
                } catch (LimiteDeLoginExcedidoException e) {
                    return false;
                }
            }));
            inicio.countDown();
            int admitidas = 0;
            for (var resultado : resultados) if (resultado.get(10, TimeUnit.SECONDS)) admitidas++;
            assertThat(admitidas).isEqualTo(3);
        }
    }

    @Test
    void retrocederElRelojNoLiberaCupos() {
        agotar(limite, "origen", "email");
        reloj.avanzar(Duration.ofSeconds(-30));
        assertThrows(LimiteDeLoginExcedidoException.class, () -> limite.verificar("origen", "email"));
        reloj.avanzar(Duration.ofSeconds(90));
        agotar(limite, "origen", "email");
    }

    @Test
    void configuracionNoPermiteUnCupoQueAlcanceElBloqueoDeRnf07() {
        assertThatThrownBy(() -> new LimitadorDeLoginPorEmail(
                new PropiedadesDeLimiteDeLogin(5, Duration.ofSeconds(60), 10), reloj, ReglasDePrueba.DE_HOY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void configuracionRechazaValoresNoPositivos() {
        assertThatThrownBy(() -> new PropiedadesDeLimiteDeLogin(0, Duration.ofSeconds(60), 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PropiedadesDeLimiteDeLogin(3, Duration.ZERO, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PropiedadesDeLimiteDeLogin(3, Duration.ofSeconds(-1), 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PropiedadesDeLimiteDeLogin(3, null, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PropiedadesDeLimiteDeLogin(3, Duration.ofSeconds(60), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
