package com.chitgo.pedidos.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.chitgo.compartido.dominio.RelojDePrueba;
import com.chitgo.pedidos.dominio.EventoTiempoReal;
import com.chitgo.pedidos.dominio.TipoEvento;
import com.chitgo.pedidos.dominio.puertos.PedidosDemorados;
import com.chitgo.pedidos.dominio.puertos.PedidosDemorados.PedidoPendiente;
import com.chitgo.pedidos.dominio.puertos.PublicadorDeEventos;

class AlertaDeDemoraServiceTest {

    private static final long ESQUINA = 1L;
    private static final long PUERTO = 2L;

    private final RelojDePrueba reloj = new RelojDePrueba(hora("20:02:55"));
    private final List<Cargado> pendientes = new ArrayList<>();
    private boolean baseCaida;

    /** Puerto falso: filtra los pendientes cargados en el test por la ventana, como la consulta. */
    private final PedidosDemorados puerto = (despuesDe, hasta) -> {
        if (baseCaida) {
            throw new IllegalStateException("La base no responde");
        }
        return pendientes.stream()
                .filter(c -> c.enviadoEn().isAfter(despuesDe) && !c.enviadoEn().isAfter(hasta))
                .map(Cargado::pendiente)
                .toList();
    };
    private final PublicadorDeEventos eventos = mock(PublicadorDeEventos.class);
    private final AlertaDeDemoraService servicio = new AlertaDeDemoraService(puerto, eventos, reloj);

    private record Cargado(PedidoPendiente pendiente, Instant enviadoEn) {
    }

    private record Enviado(Long localId, EventoTiempoReal evento) {
    }

    private static Instant hora(String hora) {
        return Instant.parse("2026-10-20T" + hora + "Z");
    }

    private void pendiente(long id, long localId, String enviadoEn) {
        pendientes.add(new Cargado(new PedidoPendiente(id, localId, id * 10), hora(enviadoEn)));
    }

    /** Lleva el reloj a esa hora, corre una pasada y devuelve lo que publicó a las cajas. */
    private List<Enviado> pasadaALas(String hora) {
        reloj.avanzar(Duration.between(reloj.ahora(), hora(hora)));
        servicio.revisarDemorados();
        ArgumentCaptor<Long> locales = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<EventoTiempoReal> enviados = ArgumentCaptor.forClass(EventoTiempoReal.class);
        verify(eventos, atLeast(0)).aCaja(locales.capture(), enviados.capture());
        verifyNoMoreInteractions(eventos);
        clearInvocations(eventos);
        return IntStream.range(0, locales.getAllValues().size())
                .mapToObj(i -> new Enviado(locales.getAllValues().get(i), enviados.getAllValues().get(i)))
                .toList();
    }

    private static EventoTiempoReal alertaALas200301(Long pedidoId, Long mesaId) {
        return new EventoTiempoReal(TipoEvento.ALERTA_DEMORA, pedidoId, mesaId, null, "PENDIENTE_APROBACION", null,
                "2026-10-20T20:03:01Z");
    }

    private static List<Long> pedidos(List<Enviado> enviados) {
        return enviados.stream().map(e -> e.evento().pedidoId()).toList();
    }

    @Test
    void la_primera_pasada_no_avisa_aunque_haya_demorados() {
        pendiente(1, ESQUINA, "19:50:00");

        servicio.revisarDemorados();

        verifyNoInteractions(eventos);
        assertTrue(pasadaALas("20:03:00").isEmpty(), "ya estaba demorado antes de la primera pasada: sale en el GET");
    }

    @Test
    void CP_20_01_y_CP_20_02_avisa_a_los_181_segundos_y_una_sola_vez() {
        pendiente(1, ESQUINA, "20:00:00");
        servicio.revisarDemorados(); // 20:02:55, solo anota la hora

        assertTrue(pasadaALas("20:02:59").isEmpty()); // CP-20-02
        assertTrue(pasadaALas("20:03:00").isEmpty()); // 180 segundos: todavía no
        List<Enviado> aLas200301 = pasadaALas("20:03:01");
        assertTrue(pasadaALas("20:03:06").isEmpty()); // sin repetir

        assertEquals(List.of(new Enviado(ESQUINA, alertaALas200301(1L, 10L))), aLas200301);
    }

    @Test
    void CP_20_01_si_ninguna_pasada_cae_justo_a_los_181_avisa_la_primera_despues() {
        pendiente(1, ESQUINA, "20:00:00");
        servicio.revisarDemorados(); // 20:02:55

        assertTrue(pasadaALas("20:02:58").isEmpty());
        assertEquals(List.of(1L), pedidos(pasadaALas("20:03:03")));
        assertTrue(pasadaALas("20:03:08").isEmpty());
    }

    @Test
    void CP_20_03_un_pedido_aprobado_antes_de_los_3_minutos_no_recibe_aviso() {
        pendiente(1, ESQUINA, "20:00:00");
        servicio.revisarDemorados(); // 20:02:55

        pendientes.clear(); // Carla lo aprueba: la consulta trae solo pendientes

        assertTrue(pasadaALas("20:03:01").isEmpty());
        assertTrue(pasadaALas("20:03:06").isEmpty());
    }

    @Test
    void con_dos_locales_cada_aviso_va_a_la_caja_de_su_local() {
        pendiente(1, ESQUINA, "20:00:00");
        pendiente(2, PUERTO, "20:00:00");
        servicio.revisarDemorados(); // 20:02:55

        assertEquals(List.of(
                new Enviado(ESQUINA, alertaALas200301(1L, 10L)),
                new Enviado(PUERTO, alertaALas200301(2L, 20L))), pasadaALas("20:03:01"));
    }

    @Test
    void una_pasada_atrasada_avisa_todo_lo_que_cruzo_en_el_medio_sin_perder_ni_repetir() {
        pendiente(1, ESQUINA, "20:00:00");
        pendiente(2, ESQUINA, "20:00:03"); // justo en el borde de la segunda pasada
        pendiente(3, ESQUINA, "20:00:04");
        pendiente(4, PUERTO, "20:00:09");
        pendiente(5, ESQUINA, "20:00:15"); // justo en el borde de la pasada atrasada
        pendiente(6, ESQUINA, "20:00:16");
        servicio.revisarDemorados(); // 20:02:55
        assertTrue(pasadaALas("20:02:59").isEmpty());

        assertEquals(List.of(1L, 2L), pedidos(pasadaALas("20:03:04")));
        assertEquals(List.of(3L, 4L, 5L), pedidos(pasadaALas("20:03:16"))); // 12 segundos en vez de 5
        assertEquals(List.of(6L), pedidos(pasadaALas("20:03:21")));
        assertTrue(pasadaALas("20:03:26").isEmpty());
    }

    @Test
    void si_la_base_falla_la_hora_no_avanza_y_la_pasada_siguiente_mira_esa_ventana() {
        pendiente(1, ESQUINA, "20:00:00");
        servicio.revisarDemorados(); // 20:02:55
        assertTrue(pasadaALas("20:02:58").isEmpty());

        baseCaida = true;
        reloj.avanzar(Duration.ofSeconds(5)); // 20:03:03
        assertThrows(IllegalStateException.class, servicio::revisarDemorados);
        verifyNoInteractions(eventos);

        baseCaida = false;
        assertEquals(List.of(1L), pedidos(pasadaALas("20:03:08")));
        assertTrue(pasadaALas("20:03:13").isEmpty());
    }

    @Test
    void si_el_reloj_no_avanza_no_repite_avisos() {
        pendiente(1, ESQUINA, "20:00:00");
        servicio.revisarDemorados(); // 20:02:55

        assertEquals(List.of(1L), pedidos(pasadaALas("20:03:01")));
        assertTrue(pasadaALas("20:03:01").isEmpty());
    }
}
