package com.chitgo.pedidos.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.chitgo.compartido.dominio.RelojDePrueba;
import com.chitgo.pedidos.dominio.Cuenta;
import com.chitgo.pedidos.dominio.EstadoCuenta;
import com.chitgo.pedidos.dominio.puertos.CuentaRepository;
import com.chitgo.usuarios.dominio.puertos.EmisorDeHabilitaciones;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeDispositivo;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeSesion;

/**
 * HU-10 de punta a punta (CP-22-01 a CP-22-07, la confirmación con un pedido nuevo, dos cierres a la vez, otro local
 * y el CSRF), más el listado de cuentas por cobrar y la reimpresión contra el MySQL. Caja, Cocina y el mozo entran
 * con el PIN de verdad y las cuentas se arman por el camino del cliente, con el QR de una mesa de la seed. Contra el
 * MySQL del Compose y R__datos_de_prueba.sql, sin {@code @Transactional}, como AprobacionDePedidosTest: los dos
 * cierres solo chocan con dos commits de verdad. Cada test borra lo que dejó.
 *
 * <p>Dos pasos van por SQL porque el back todavía no los tiene: servir un pedido (HU-05, de German) y pedir la
 * cuenta (el cliente). Lo que viaja por el WebSocket no se mira acá: lo cubre CerrarCuentaServiceTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class CierreDeCuentaTest {

    private static final String CUENTAS = "/api/v1/caja/cuentas";
    private static final String TOKEN_LA_ESQUINA = "83f39b25-deac-4fde-80cc-4cfdf8117713";
    private static final String MESA_1_ESQUINA = "d4964b6b-fdae-4b4f-83e9-1c1e78a21723";
    private static final String MESA_2_ESQUINA = "7ac79c59-b48d-4db2-8365-5c6a6565d9f4";
    private static final String MESA_1_PUERTO = "ab0124a8-6033-4d73-a1f6-3513140e62f7";
    private static final String SIN_PERMISO = "No tenés permiso para hacer esto.";
    private static final String NADIE_LA_PIDIO = "Esta cuenta no la pidió nadie. ¿La cerramos igual?";
    private static final String SIN_SERVIR = "Hay pedidos sin servir. ¿Estás seguro de que querés cerrar la cuenta?";
    /** Lo que se borra al terminar, en el orden de las FK: el ticket apunta a la cuenta. */
    private static final List<String> TABLAS_DE_LA_PRUEBA = List.of(
            "registro_auditoria", "ticket", "item_pedido", "pedido", "cliente_sesion", "aviso", "cuenta");

    /**
     * El CuentaRepository de verdad, que en los dos cierres a la vez frena las dos primeras lecturas hasta que lleguen
     * las dos. Así los dos cierres leen la cuenta sin cerrar y con la misma versión antes de que cualquiera guarde, y
     * el segundo choca con el @Version. Fuera de ese test no frena nada.
     */
    static class CuentasConBarrera implements CuentaRepository {

        record Lectura(EstadoCuenta estado, Integer version) {
        }

        private final CuentaRepository real;
        private final AtomicInteger porFrenar = new AtomicInteger();
        private final List<Lectura> lecturas = new CopyOnWriteArrayList<>();
        private volatile CyclicBarrier barrera;

        CuentasConBarrera(CuentaRepository real) {
            this.real = real;
        }

        void frenarLasProximasDosLecturas() {
            lecturas.clear();
            barrera = new CyclicBarrier(2);
            porFrenar.set(2);
        }

        void soltar() {
            porFrenar.set(0);
        }

        List<Lectura> lecturas() {
            return List.copyOf(lecturas);
        }

        @Override
        public Optional<Cuenta> buscarPorId(Long id, Long localId) {
            Optional<Cuenta> cuenta = real.buscarPorId(id, localId);
            if (porFrenar.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                cuenta.ifPresent(c -> lecturas.add(new Lectura(c.getEstado(), c.getVersion())));
                esperarAlOtro();
            }
            return cuenta;
        }

        /** Con tope: si el otro hilo no llega, el test falla en vez de colgarse. */
        private void esperarAlOtro() {
            try {
                barrera.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } catch (BrokenBarrierException | TimeoutException e) {
                throw new IllegalStateException("El otro cierre no llegó a leer la cuenta", e);
            }
        }

        @Override
        public List<Cuenta> listarDelLocal(Long localId, Collection<EstadoCuenta> estados) {
            return real.listarDelLocal(localId, estados);
        }

        @Override
        public Optional<Cuenta> buscarNoCerradaDeMesa(Long mesaId) {
            return real.buscarNoCerradaDeMesa(mesaId);
        }

        @Override
        public Cuenta guardar(Cuenta cuenta) {
            return real.guardar(cuenta);
        }

        @Override
        public void subirVersion(Cuenta cuenta) {
            real.subirVersion(cuenta);
        }
    }

    @TestConfiguration
    static class ConRelojYBarrera {

        @Bean
        @Primary
        RelojDePrueba relojDePrueba() {
            // Sin fracciones de segundo, como guardan las columnas TIMESTAMP.
            return new RelojDePrueba(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        }

        @Bean
        @Primary
        CuentasConBarrera cuentasConBarrera(@Qualifier("cuentaRepositoryAdapter") CuentaRepository real) {
            return new CuentasConBarrera(real);
        }
    }

    /** Un plato y cuántos, para armar un pedido. */
    private record Renglon(Long platoId, int cantidad) {
    }

    /** La mesa abierta por el celular A: su token de sesión y la cuenta que abrió. */
    private record Mesa(String tokenSesion, Long cuentaId) {
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EmisorDeHabilitaciones habilitaciones;

    @Autowired
    private RelojDePrueba reloj;

    @Autowired
    private CuentasConBarrera cuentas;

    private final Map<String, Long> ultimoIdAntes = new HashMap<>();
    private final Map<Long, BigDecimal> preciosOriginales = new HashMap<>();

    @BeforeEach
    void anotarLoQueHabia() {
        for (String tabla : TABLAS_DE_LA_PRUEBA) {
            ultimoIdAntes.put(tabla, jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM " + tabla, Long.class));
        }
    }

    @AfterEach
    void dejarLaBaseComoEnLaSeed() {
        cuentas.soltar();
        for (String tabla : TABLAS_DE_LA_PRUEBA) {
            jdbc.update("DELETE FROM " + tabla + " WHERE id > ?", ultimoIdAntes.get(tabla));
        }
        preciosOriginales.forEach((plato, precio) -> jdbc.update("UPDATE plato SET precio = ? WHERE id = ?", precio, plato));
    }

    // --- Cerrar

    @Test
    void carlaCierraUnaCuentaSolicitadaYSaleElTicketCompleto() throws Exception {
        // CP-22-01: 2 Milanesas y 1 Flan servidos, en dos pedidos.
        Mesa mesa = abrirMesa(MESA_1_ESQUINA);
        Cookie carla = sesionDe("Carla", "2468");
        Cookie hugo = sesionDe("Hugo", "1357");
        servir(carla, hugo, pedir(mesa, milanesas(2)));
        servir(carla, hugo, pedir(mesa, new Renglon(plato("Flan casero", "La Esquina"), 1)));
        pedirLaCuenta(mesa);

        String respuesta = cerrar(carla, mesa.cuentaId(), null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Map<String, Object> ticket = JsonPath.read(respuesta, "$");
        Long ticketId = jdbc.queryForObject("SELECT id FROM ticket WHERE cuenta_id = ?", Long.class, mesa.cuentaId());
        assertThat(((Number) ticket.get("numero")).longValue()).isEqualTo(ticketId);
        assertThat(ticket).containsEntry("local", "La Esquina")
                .containsEntry("mesa", "1")
                .containsEntry("emitidoEn", reloj.ahora().toString())
                .containsEntry("letra", "X")
                .containsEntry("leyenda", "DOCUMENTO NO VÁLIDO COMO FACTURA");
        assertThat(decimal(ticket.get("total"))).isEqualByComparingTo("23000");
        assertThat(renglones(respuesta)).containsExactly(
                "Milanesa napolitana x2 a 9500.00 = 19000.00",
                "Flan casero x1 a 4000.00 = 4000.00");

        Map<String, Object> cuenta = jdbc.queryForMap("""
                SELECT estado, total, cerrada_por_usuario_id, UNIX_TIMESTAMP(cerrada_en) AS cerrada_en
                FROM cuenta WHERE id = ?""", mesa.cuentaId());
        assertThat(cuenta.get("estado")).isEqualTo("CERRADA");
        assertThat(decimal(cuenta.get("total"))).isEqualByComparingTo("23000");
        assertThat(((Number) cuenta.get("cerrada_por_usuario_id")).longValue()).isEqualTo(idDe("Carla"));
        assertThat(((Number) cuenta.get("cerrada_en")).longValue()).isEqualTo(reloj.ahora().getEpochSecond());
        assertThat(decimal(jdbc.queryForObject("SELECT total FROM ticket WHERE id = ?", BigDecimal.class, ticketId)))
                .isEqualByComparingTo("23000");

        Map<String, Object> registro = unicoRegistro();
        assertThat(registro.get("accion")).isEqualTo("CERRAR_CUENTA");
        assertThat(registro.get("entidad")).isEqualTo("CUENTA");
        assertThat(((Number) registro.get("entidad_id")).longValue()).isEqualTo(mesa.cuentaId());
        assertThat(((Number) registro.get("usuario_id")).longValue()).isEqualTo(idDe("Carla"));
        assertThat(registro.get("antes")).isEqualTo("SOLICITADA");
        assertThat(registro.get("despues")).isEqualTo("CERRADA");
        assertThat(decimal(registro.get("total"))).isEqualByComparingTo("23000");
        assertThat(Long.valueOf(registro.get("ticket_id").toString())).isEqualTo(ticketId);
        assertThat(cancelados(registro)).isEmpty();

        // La mesa 1 queda libre: el QR ya no encuentra cuenta y Caja ya no la tiene por cobrar.
        mvc.perform(get("/api/v1/public/mesas/" + MESA_1_ESQUINA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuentaAbierta").value(false));
        mvc.perform(get(CUENTAS).cookie(carla))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(not(hasItem(mesa.cuentaId().intValue()))));
    }

    @Test
    void unPedidoRechazadoNoApareceEnElTicketNiSuma() throws Exception {
        // CP-22-02
        Mesa mesa = abrirMesa(MESA_1_ESQUINA);
        Cookie carla = sesionDe("Carla", "2468");
        servir(carla, sesionDe("Hugo", "1357"), pedir(mesa, milanesas(2)));
        Long rechazado = pedir(mesa, new Renglon(plato("Ensalada mixta", "La Esquina"), 1));
        enviar(post("/api/v1/caja/pedidos/" + rechazado + "/rechazar").cookie(carla)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\": \"MESA_NO_OCUPADA\"}"))
                .andExpect(status().isNoContent());
        pedirLaCuenta(mesa);

        String respuesta = cerrar(carla, mesa.cuentaId(), null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(renglones(respuesta)).containsExactly("Milanesa napolitana x2 a 9500.00 = 19000.00");
        assertThat(decimal(JsonPath.read(respuesta, "$.total"))).isEqualByComparingTo("19000");
        assertThat(estadoDe(rechazado)).isEqualTo("RECHAZADO");
        assertThat(cancelados(unicoRegistro())).isEmpty();
    }

    @Test
    void elTicketUsaElPrecioDelMomentoDelPedido() throws Exception {
        // CP-22-03: el Admin sube la Milanesa después del pedido. Se sube por SQL porque el ABM del menú no existe.
        Mesa mesa = abrirMesa(MESA_1_ESQUINA);
        Cookie carla = sesionDe("Carla", "2468");
        servir(carla, sesionDe("Hugo", "1357"), pedir(mesa, milanesas(1)));
        cambiarPrecio(plato("Milanesa napolitana", "La Esquina"), new BigDecimal("10500.00"));
        pedirLaCuenta(mesa);

        String respuesta = cerrar(carla, mesa.cuentaId(), null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(renglones(respuesta)).containsExactly("Milanesa napolitana x1 a 9500.00 = 9500.00");
        assertThat(decimal(JsonPath.read(respuesta, "$.total"))).isEqualByComparingTo("9500");
        assertThat(decimal(jdbc.queryForObject("SELECT total FROM cuenta WHERE id = ?", BigDecimal.class,
                mesa.cuentaId()))).isEqualByComparingTo("9500");
    }

    @Test
    void despuesDelCierreElPedidoDeADa409YUnCelularNuevoAbreOtraCuenta() throws Exception {
        // CP-22-04
        Mesa a = abrirMesa(MESA_1_ESQUINA);
        Cookie carla = sesionDe("Carla", "2468");
        servir(carla, sesionDe("Hugo", "1357"), pedir(a, milanesas(1)));
        pedirLaCuenta(a);
        cerrar(carla, a.cuentaId(), null).andExpect(status().isOk());
        int pedidosAntes = pedidosNuevos();

        enviarPedido(a, milanesas(1))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("La cuenta de esta mesa ya se cerró"));
        assertThat(pedidosNuevos()).isEqualTo(pedidosAntes);

        Mesa nuevo = abrirMesa(MESA_1_ESQUINA);
        assertThat(nuevo.cuentaId()).isNotEqualTo(a.cuentaId());
        assertThat(jdbc.queryForObject("SELECT estado FROM cuenta WHERE id = ?", String.class, nuevo.cuentaId()))
                .isEqualTo("ABIERTA");
        assertThat(estadoDeCuenta(a.cuentaId())).isEqualTo("CERRADA");
    }

    // --- La confirmación

    @Test
    void unaCuentaAbiertaPideConfirmacionYConfirmadaSeCierra() throws Exception {
        // CP-22-06: todo servido, así el único motivo es que nadie pidió la cuenta.
        Mesa mesa = abrirMesa(MESA_1_ESQUINA);
        Cookie carla = sesionDe("Carla", "2468");
        servir(carla, sesionDe("Hugo", "1357"), pedir(mesa, milanesas(1)));

        cerrar(carla, mesa.cuentaId(), null)
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("CONFIRMAR_CIERRE"))
                .andExpect(jsonPath("$.motivos.length()").value(1))
                .andExpect(jsonPath("$.motivos[0]").value("NADIE_PIDIO_LA_CUENTA"))
                .andExpect(jsonPath("$.pedidosSinServir").isEmpty())
                .andExpect(jsonPath("$.detail").value(NADIE_LA_PIDIO));
        nadaCambio(mesa, "ABIERTA");

        String respuesta = cerrar(carla, mesa.cuentaId(), "{\"confirmar\": true}")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(renglones(respuesta)).containsExactly("Milanesa napolitana x1 a 9500.00 = 9500.00");
        assertThat(estadoDeCuenta(mesa.cuentaId())).isEqualTo("CERRADA");
        assertThat(ticketsNuevos()).isEqualTo(1);
        assertThat(unicoRegistro().get("antes")).isEqualTo("ABIERTA");
    }

    @Test
    void unPedidoEnPreparacionSeCancelaAlConfirmar() throws Exception {
        // CP-22-07: lo que no se sirvió se cancela al confirmar (decidido el 29/10).
        Mesa mesa = abrirMesa(MESA_1_ESQUINA);
        Cookie carla = sesionDe("Carla", "2468");
        Cookie hugo = sesionDe("Hugo", "1357");
        servir(carla, hugo, pedir(mesa, milanesas(2)));
        Long enPreparacion = pedir(mesa, new Renglon(plato("Flan casero", "La Esquina"), 2));
        aprobar(carla, enPreparacion);
        cocina(hugo, enPreparacion, "en-preparacion");
        pedirLaCuenta(mesa);

        cerrar(carla, mesa.cuentaId(), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFIRMAR_CIERRE"))
                .andExpect(jsonPath("$.motivos.length()").value(1))
                .andExpect(jsonPath("$.motivos[0]").value("PEDIDOS_SIN_SERVIR"))
                .andExpect(jsonPath("$.detail").value(SIN_SERVIR))
                .andExpect(jsonPath("$.pedidosSinServir.length()").value(1))
                .andExpect(jsonPath("$.pedidosSinServir[0].id").value(enPreparacion))
                .andExpect(jsonPath("$.pedidosSinServir[0].estado").value("EN_PREPARACION"))
                .andExpect(jsonPath("$.pedidosSinServir[0].platos[0].plato").value("Flan casero"))
                .andExpect(jsonPath("$.pedidosSinServir[0].platos[0].cantidad").value(2));
        nadaCambio(mesa, "SOLICITADA");
        assertThat(estadoDe(enPreparacion)).isEqualTo("EN_PREPARACION");

        String respuesta = cerrar(carla, mesa.cuentaId(), confirmando(enPreparacion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Map<String, Object> pedido = jdbc.queryForMap("""
                SELECT estado, cancelado_por_usuario_id, UNIX_TIMESTAMP(cancelado_en) AS cancelado_en
                FROM pedido WHERE id = ?""", enPreparacion);
        assertThat(pedido.get("estado")).isEqualTo("CANCELADO");
        assertThat(((Number) pedido.get("cancelado_por_usuario_id")).longValue()).isEqualTo(idDe("Carla"));
        assertThat(((Number) pedido.get("cancelado_en")).longValue()).isEqualTo(reloj.ahora().getEpochSecond());
        mvc.perform(get("/api/v1/cocina/pedidos").cookie(hugo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enCola[*].id").value(not(hasItem(enPreparacion.intValue()))))
                .andExpect(jsonPath("$.enPreparacion[*].id").value(not(hasItem(enPreparacion.intValue()))))
                .andExpect(jsonPath("$.listos[*].id").value(not(hasItem(enPreparacion.intValue()))));

        assertThat(renglones(respuesta)).containsExactly("Milanesa napolitana x2 a 9500.00 = 19000.00");
        assertThat(decimal(JsonPath.read(respuesta, "$.total"))).isEqualByComparingTo("19000");
        Map<String, Object> registro = unicoRegistro();
        assertThat(registro.get("antes")).isEqualTo("SOLICITADA");
        assertThat(cancelados(registro)).containsExactly(enPreparacion);
    }

    @Test
    void unPedidoQueEntraAntesDeConfirmarVuelveAFrenar() throws Exception {
        // Carla vio p1; entre el 409 y la confirmación el cliente manda p2. Frena solo lo nuevo que no vio (29/10).
        Mesa mesa = abrirMesa(MESA_1_ESQUINA);
        Cookie carla = sesionDe("Carla", "2468");
        Long p1 = pedir(mesa, milanesas(1));

        cerrar(carla, mesa.cuentaId(), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.motivos[0]").value("NADIE_PIDIO_LA_CUENTA"))
                .andExpect(jsonPath("$.motivos[1]").value("PEDIDOS_SIN_SERVIR"))
                .andExpect(jsonPath("$.detail").value(NADIE_LA_PIDIO + " " + SIN_SERVIR))
                .andExpect(jsonPath("$.pedidosSinServir[*].id", contains(p1.intValue())));

        Long p2 = pedir(mesa, new Renglon(plato("Flan casero", "La Esquina"), 1));

        cerrar(carla, mesa.cuentaId(), confirmando(p1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFIRMAR_CIERRE"))
                .andExpect(jsonPath("$.pedidosSinServir[*].id", contains(p1.intValue(), p2.intValue())));
        nadaCambio(mesa, "ABIERTA");
        assertThat(estadoDe(p1)).isEqualTo("PENDIENTE_APROBACION");
        assertThat(estadoDe(p2)).isEqualTo("PENDIENTE_APROBACION");

        // Con lo que vio ahora, se cierra y los dos quedan cancelados.
        String respuesta = cerrar(carla, mesa.cuentaId(), confirmando(p1, p2))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(renglones(respuesta)).isEmpty();
        assertThat(decimal(JsonPath.read(respuesta, "$.total"))).isEqualByComparingTo("0");
        assertThat(estadoDe(p1)).isEqualTo("CANCELADO");
        assertThat(estadoDe(p2)).isEqualTo("CANCELADO");
        assertThat(cancelados(unicoRegistro())).containsExactlyInAnyOrder(p1, p2);
    }

    // --- Dos cierres a la vez

    @Test
    void dosCierresALaVezSoloUnoGana() throws Exception {
        // Dos sesiones de Caja, dos hilos, y la barrera para que los dos lean la cuenta antes de guardar.
        Mesa mesa = abrirMesa(MESA_1_ESQUINA);
        Cookie primeraCaja = sesionDe("Carla", "2468");
        Cookie segundaCaja = sesionDe("Carla", "2468");
        servir(primeraCaja, sesionDe("Hugo", "1357"), pedir(mesa, milanesas(1)));
        Long pendiente = pedir(mesa, new Renglon(plato("Flan casero", "La Esquina"), 1));
        pedirLaCuenta(mesa);
        Integer versionAntes = jdbc.queryForObject("SELECT version FROM cuenta WHERE id = ?", Integer.class,
                mesa.cuentaId());

        cuentas.frenarLasProximasDosLecturas();
        List<MockHttpServletResponse> respuestas;
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            Future<MockHttpServletResponse> uno = hilos.submit(() -> cerrar(primeraCaja, mesa.cuentaId(),
                    confirmando(pendiente)).andReturn().getResponse());
            Future<MockHttpServletResponse> otro = hilos.submit(() -> cerrar(segundaCaja, mesa.cuentaId(),
                    confirmando(pendiente)).andReturn().getResponse());
            respuestas = List.of(uno.get(30, TimeUnit.SECONDS), otro.get(30, TimeUnit.SECONDS));
        } finally {
            hilos.shutdownNow();
        }

        // Los dos leyeron la cuenta solicitada y con la misma versión: llegaron de verdad a la vez.
        assertThat(cuentas.lecturas()).containsExactly(
                new CuentasConBarrera.Lectura(EstadoCuenta.SOLICITADA, versionAntes),
                new CuentasConBarrera.Lectura(EstadoCuenta.SOLICITADA, versionAntes));
        assertThat(respuestas).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(200, 409);
        MockHttpServletResponse perdedora = respuestas.stream().filter(r -> r.getStatus() == 409).findFirst().orElseThrow();
        String detalle = JsonPath.read(perdedora.getContentAsString(StandardCharsets.UTF_8), "$.detail");
        assertThat(detalle).isEqualTo("La cuenta cambió justo antes. Probá de nuevo.");

        assertThat(estadoDeCuenta(mesa.cuentaId())).isEqualTo("CERRADA");
        assertThat(ticketsNuevos()).isEqualTo(1);
        assertThat(estadoDe(pendiente)).isEqualTo("CANCELADO");
        assertThat(cancelados(unicoRegistro())).containsExactly(pendiente);
    }

    // --- Quién puede

    @Test
    void elMozoNoPuedeCerrarNiVerCuentas() throws Exception {
        // CP-22-05: Juan manda el POST directo, sin pasar por ninguna pantalla. Los dos GET piden el mismo permiso.
        Mesa mesa = abrirMesa(MESA_1_ESQUINA);
        servir(sesionDe("Carla", "2468"), sesionDe("Hugo", "1357"), pedir(mesa, milanesas(1)));
        pedirLaCuenta(mesa);
        Cookie juan = sesionDe("Juan", "1234");

        sinPermiso(cerrar(juan, mesa.cuentaId(), "{\"confirmar\": true}"));
        sinPermiso(mvc.perform(get(CUENTAS).cookie(juan)));
        sinPermiso(mvc.perform(get(CUENTAS + "/" + mesa.cuentaId() + "/ticket").cookie(juan)));

        nadaCambio(mesa, "SOLICITADA");
    }

    @Test
    void unaCuentaDeElPuertoDa404ComoSiNoExistiera() throws Exception {
        // RNF-10: la misma respuesta que un id que no existe, y no aparece en el listado.
        Mesa delPuerto = abrirMesa(MESA_1_PUERTO);
        pedir(delPuerto, new Renglon(plato("Pizza muzzarella", "El Puerto"), 1));
        Cookie carla = sesionDe("Carla", "2468");

        String deOtroLocal = cerrar(carla, delPuerto.cuentaId(), "{\"confirmar\": true}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("La cuenta no existe"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String queNoExiste = cerrar(carla, 999_999L, "{\"confirmar\": true}")
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        mvc.perform(get(CUENTAS + "/" + delPuerto.cuentaId() + "/ticket").cookie(carla))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("La cuenta no existe"));
        mvc.perform(get(CUENTAS).cookie(carla))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(not(hasItem(delPuerto.cuentaId().intValue()))));

        assertThat(JsonPath.<String>read(deOtroLocal, "$.detail")).isEqualTo(JsonPath.read(queNoExiste, "$.detail"));
        assertThat(deOtroLocal).doesNotContain("Puerto");
        nadaCambio(delPuerto, "ABIERTA");
    }

    @Test
    void cerrarSinElHeaderDeCsrfDa403() throws Exception {
        Mesa mesa = abrirMesa(MESA_1_ESQUINA);
        servir(sesionDe("Carla", "2468"), sesionDe("Hugo", "1357"), pedir(mesa, milanesas(1)));
        pedirLaCuenta(mesa);

        mvc.perform(post(CUENTAS + "/" + mesa.cuentaId() + "/cerrar").cookie(sesionDe("Carla", "2468"), xsrf()))
                .andExpect(status().isForbidden());

        nadaCambio(mesa, "SOLICITADA");
    }

    // --- Cuentas por cobrar y reimpresión, contra el MySQL

    @Test
    void lasCuentasPorCobrarVienenDelMySQLEnOrdenYConSuTotal() throws Exception {
        // La mesa 1 abre primero y la 2 pide la cuenta: van primero las Solicitadas. El total suma lo servido y lo
        // sin servir, no lo rechazado.
        Mesa mesa1 = abrirMesa(MESA_1_ESQUINA);
        Mesa mesa2 = abrirMesa(MESA_2_ESQUINA);
        Cookie carla = sesionDe("Carla", "2468");
        servir(carla, sesionDe("Hugo", "1357"), pedir(mesa1, milanesas(1)));
        pedir(mesa1, new Renglon(plato("Flan casero", "La Esquina"), 2));
        Long rechazado = pedir(mesa1, new Renglon(plato("Ensalada mixta", "La Esquina"), 1));
        enviar(post("/api/v1/caja/pedidos/" + rechazado + "/rechazar").cookie(carla)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\": \"MESA_NO_OCUPADA\"}"))
                .andExpect(status().isNoContent());
        pedir(mesa2, new Renglon(plato("Ensalada mixta", "La Esquina"), 1));
        pedirLaCuenta(mesa2);

        List<Map<String, Object>> todas = cuentasDeEstaPrueba(carla, null);
        assertThat(todas).extracting(c -> ((Number) c.get("id")).longValue())
                .containsExactly(mesa2.cuentaId(), mesa1.cuentaId());
        assertThat(todas.get(0)).containsEntry("mesa", "2")
                .containsEntry("estado", "SOLICITADA")
                .containsEntry("solicitadaEn", reloj.ahora().toString())
                .containsEntry("pedidosSinServir", 1);
        assertThat(decimal(todas.get(0).get("total"))).isEqualByComparingTo("6000");
        assertThat(todas.get(1)).containsEntry("mesa", "1")
                .containsEntry("estado", "ABIERTA")
                .containsEntry("abiertaEn", reloj.ahora().toString())
                .containsEntry("pedidosSinServir", 1);
        assertThat(todas.get(1).get("solicitadaEn")).isNull();
        assertThat(decimal(todas.get(1).get("total"))).isEqualByComparingTo("17500");

        assertThat(cuentasDeEstaPrueba(carla, "SOLICITADA")).extracting(c -> ((Number) c.get("id")).longValue())
                .containsExactly(mesa2.cuentaId());
        mvc.perform(get(CUENTAS).param("estado", "CERRADA").cookie(carla))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void reimprimirDaElMismoTicketQueElCierre() throws Exception {
        Mesa mesa = abrirMesa(MESA_1_ESQUINA);
        Cookie carla = sesionDe("Carla", "2468");
        Cookie hugo = sesionDe("Hugo", "1357");
        servir(carla, hugo, pedir(mesa, milanesas(1)));
        servir(carla, hugo, pedir(mesa, milanesas(1), new Renglon(plato("Flan casero", "La Esquina"), 1)));
        pedirLaCuenta(mesa);

        mvc.perform(get(CUENTAS + "/" + mesa.cuentaId() + "/ticket").cookie(carla))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Esta cuenta todavía no tiene ticket"));

        String delCierre = cerrar(carla, mesa.cuentaId(), null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String reimpreso = mvc.perform(get(CUENTAS + "/" + mesa.cuentaId() + "/ticket").cookie(carla))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(JsonPath.<Map<String, Object>>read(reimpreso, "$"))
                .isEqualTo(JsonPath.<Map<String, Object>>read(delCierre, "$"));
        assertThat(renglones(reimpreso)).containsExactly(
                "Milanesa napolitana x2 a 9500.00 = 19000.00",
                "Flan casero x1 a 4000.00 = 4000.00");
    }

    // --- Ayudas: el cliente

    /** El celular escanea el QR de una mesa libre y abre la cuenta: queda como iniciador. */
    private Mesa abrirMesa(String tokenQr) throws Exception {
        Cookie xsrf = xsrf();
        String respuesta = mvc.perform(post("/api/v1/public/mesas/" + tokenQr + "/sesiones").cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.esIniciador").value(true))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String tokenSesion = JsonPath.read(respuesta, "$.tokenSesion");
        Long cuentaId = jdbc.queryForObject("SELECT cuenta_id FROM cliente_sesion WHERE token_sesion = ?", Long.class,
                tokenSesion);
        return new Mesa(tokenSesion, cuentaId);
    }

    /** Manda un pedido con la sesión del iniciador. Sale con la hora del reloj de prueba. */
    private Long pedir(Mesa mesa, Renglon... renglones) throws Exception {
        String respuesta = enviarPedido(mesa, renglones)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE_APROBACION"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
    }

    private ResultActions enviarPedido(Mesa mesa, Renglon... renglones) throws Exception {
        String items = Arrays.stream(renglones)
                .map(r -> "{\"platoId\": %d, \"cantidad\": %d}".formatted(r.platoId(), r.cantidad()))
                .collect(Collectors.joining(", "));
        String cuerpo = "{\"uuidCliente\": \"%s\", \"items\": [%s]}".formatted(UUID.randomUUID(), items);
        Cookie xsrf = xsrf();
        return mvc.perform(post("/api/v1/public/pedidos").cookie(xsrf)
                .header("X-XSRF-TOKEN", xsrf.getValue()).header("X-Session-Token", mesa.tokenSesion())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    /**
     * El cliente pide la cuenta. Va por SQL porque en el back todavía nadie pasa una cuenta a Solicitada; sube la
     * versión como lo haría el @Version.
     */
    private void pedirLaCuenta(Mesa mesa) {
        jdbc.update("UPDATE cuenta SET estado = 'SOLICITADA', solicitada_en = ?, version = version + 1 WHERE id = ?",
                Timestamp.from(reloj.ahora()), mesa.cuentaId());
    }

    private Renglon milanesas(int cantidad) {
        return new Renglon(plato("Milanesa napolitana", "La Esquina"), cantidad);
    }

    // --- Ayudas: el personal

    /**
     * Lleva un pedido hasta Servido: Caja lo aprueba y Cocina lo prepara por HTTP, y el mozo lo sirve por SQL porque
     * POST /mozo/pedidos/{id}/servido (HU-05, de German) todavía no está en develop.
     */
    private void servir(Cookie caja, Cookie cocina, Long pedidoId) throws Exception {
        aprobar(caja, pedidoId);
        cocina(cocina, pedidoId, "en-preparacion");
        cocina(cocina, pedidoId, "listo");
        jdbc.update("""
                UPDATE pedido SET estado = 'SERVIDO', servido_en = ?, servido_por_usuario_id = ?, version = version + 1
                WHERE id = ? AND estado = 'LISTO'""", Timestamp.from(reloj.ahora()), idDe("Juan"), pedidoId);
        assertThat(estadoDe(pedidoId)).isEqualTo("SERVIDO");
    }

    private void aprobar(Cookie caja, Long pedidoId) throws Exception {
        enviar(post("/api/v1/caja/pedidos/" + pedidoId + "/aprobar").cookie(caja)).andExpect(status().isNoContent());
    }

    private void cocina(Cookie cocina, Long pedidoId, String accion) throws Exception {
        enviar(post("/api/v1/cocina/pedidos/" + pedidoId + "/" + accion).cookie(cocina))
                .andExpect(status().isNoContent());
    }

    /** POST /caja/cuentas/{id}/cerrar con el header de CSRF; sin cuerpo si es null. */
    private ResultActions cerrar(Cookie sesion, Long cuentaId, String cuerpo) throws Exception {
        MockHttpServletRequestBuilder pedido = post(CUENTAS + "/" + cuentaId + "/cerrar").cookie(sesion);
        if (cuerpo != null) {
            pedido.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        return enviar(pedido);
    }

    private static String confirmando(Long... pedidosACancelar) {
        String ids = Arrays.stream(pedidosACancelar).map(String::valueOf).collect(Collectors.joining(", "));
        return "{\"confirmar\": true, \"pedidosACancelar\": [%s]}".formatted(ids);
    }

    /** Suma la cookie y el header de CSRF, como cualquier POST del front. */
    private ResultActions enviar(MockHttpServletRequestBuilder pedido) throws Exception {
        Cookie xsrf = xsrf();
        return mvc.perform(pedido.cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()));
    }

    /** Las cuentas por cobrar de Carla, solo las que armó este test y en el orden en que vienen. */
    private List<Map<String, Object>> cuentasDeEstaPrueba(Cookie sesion, String estado) throws Exception {
        MockHttpServletRequestBuilder pedido = get(CUENTAS).cookie(sesion);
        if (estado != null) {
            pedido.param("estado", estado);
        }
        String respuesta = mvc.perform(pedido)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Map<String, Object>> cuentasDelLocal = JsonPath.read(respuesta, "$");
        return cuentasDelLocal.stream()
                .filter(c -> ((Number) c.get("id")).longValue() > ultimoIdAntes.get("cuenta"))
                .toList();
    }

    /** Cada renglón del ticket en una línea, para comparar el orden y los números de un vistazo. */
    private static List<String> renglones(String ticket) {
        List<Map<String, Object>> items = JsonPath.read(ticket, "$.items");
        return items.stream()
                .map(i -> "%s x%s a %s = %s".formatted(i.get("plato"), i.get("cantidad"),
                        decimal(i.get("precioUnitario")).setScale(2), decimal(i.get("subtotal")).setScale(2)))
                .toList();
    }

    private static BigDecimal decimal(Object valor) {
        return new BigDecimal(valor.toString());
    }

    /** El 403 por falta de permiso, y no el de la credencial provisoria (ver ControlDePermisosTest). */
    private void sinPermiso(ResultActions respuesta) throws Exception {
        respuesta.andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(SIN_PERMISO))
                .andExpect(jsonPath("$.codigo").doesNotExist());
    }

    /** Entra con el PIN como el front, con la cookie de dispositivo del usuario, como en ControlDePermisosTest. */
    private Cookie sesionDe(String nombre, String pin) throws Exception {
        Long usuarioId = idDe(nombre);
        Cookie xsrf = xsrf();
        Cookie dispositivo = new Cookie(CookieDeDispositivo.NOMBRE, habilitaciones.emitir(usuarioId));
        String cuerpo = """
                {"tokenStaff": "%s", "usuarioId": %d, "pin": "%s"}""".formatted(TOKEN_LA_ESQUINA, usuarioId, pin);
        Cookie sesion = mvc.perform(post("/api/v1/auth/pin").cookie(xsrf, dispositivo)
                        .header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
        assertThat(sesion).isNotNull();
        return sesion;
    }

    private Cookie xsrf() throws Exception {
        return mvc.perform(get("/api/v1/ping")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    // --- Ayudas: la base

    private Long idDe(String nombre) {
        return jdbc.queryForObject("""
                SELECT u.id FROM usuario u JOIN local l ON l.id = u.local_id
                WHERE u.nombre = ? AND l.nombre = 'La Esquina'""", Long.class, nombre);
    }

    private Long plato(String nombre, String local) {
        return jdbc.queryForObject("""
                SELECT p.id FROM plato p JOIN local l ON l.id = p.local_id
                WHERE p.nombre = ? AND l.nombre = ?""", Long.class, nombre, local);
    }

    /** Cambia el precio de la carta y anota el de antes para dejarlo como en la seed. */
    private void cambiarPrecio(Long platoId, BigDecimal precio) {
        preciosOriginales.putIfAbsent(platoId,
                jdbc.queryForObject("SELECT precio FROM plato WHERE id = ?", BigDecimal.class, platoId));
        jdbc.update("UPDATE plato SET precio = ? WHERE id = ?", precio, platoId);
    }

    private String estadoDe(Long pedidoId) {
        return jdbc.queryForObject("SELECT estado FROM pedido WHERE id = ?", String.class, pedidoId);
    }

    private String estadoDeCuenta(Long cuentaId) {
        return jdbc.queryForObject("SELECT estado FROM cuenta WHERE id = ?", String.class, cuentaId);
    }

    /** Un 409 o un 403 no deja nada: la cuenta sigue igual, sin total, sin ticket y sin auditoría. */
    private void nadaCambio(Mesa mesa, String estado) {
        Map<String, Object> cuenta = jdbc.queryForMap(
                "SELECT estado, total, cerrada_en, cerrada_por_usuario_id FROM cuenta WHERE id = ?", mesa.cuentaId());
        assertThat(cuenta.get("estado")).isEqualTo(estado);
        assertThat(cuenta.get("total")).isNull();
        assertThat(cuenta.get("cerrada_en")).isNull();
        assertThat(cuenta.get("cerrada_por_usuario_id")).isNull();
        assertThat(ticketsNuevos()).isZero();
        assertThat(cierresAuditados()).isZero();
    }

    private int pedidosNuevos() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM pedido WHERE id > ?", Integer.class,
                ultimoIdAntes.get("pedido"));
    }

    private int ticketsNuevos() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ticket WHERE id > ?", Integer.class,
                ultimoIdAntes.get("ticket"));
    }

    /** Solo los del cierre: aprobar y rechazar al armar la cuenta dejan los suyos. */
    private int cierresAuditados() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM registro_auditoria WHERE id > ? AND accion = 'CERRAR_CUENTA'""", Integer.class,
                ultimoIdAntes.get("registro_auditoria"));
    }

    /** El registro del cierre que dejó el test, leído con SQL: así se ve el JSON que quedó en la columna. */
    private Map<String, Object> unicoRegistro() {
        assertThat(cierresAuditados()).isEqualTo(1);
        return jdbc.queryForMap("""
                SELECT usuario_id, accion, entidad, entidad_id, valor_anterior ->> '$.estado' AS antes,
                       valor_nuevo ->> '$.estado' AS despues, valor_nuevo ->> '$.total' AS total,
                       valor_nuevo ->> '$.ticketId' AS ticket_id, valor_nuevo ->> '$.pedidosCancelados' AS cancelados
                FROM registro_auditoria WHERE id > ? AND accion = 'CERRAR_CUENTA'""",
                ultimoIdAntes.get("registro_auditoria"));
    }

    private static List<Long> cancelados(Map<String, Object> registro) {
        List<Number> ids = JsonPath.read(registro.get("cancelados").toString(), "$");
        return ids.stream().map(Number::longValue).toList();
    }
}
