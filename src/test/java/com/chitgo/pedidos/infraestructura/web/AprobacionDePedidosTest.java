package com.chitgo.pedidos.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

import com.chitgo.compartido.dominio.RelojDePrueba;
import com.chitgo.pedidos.dominio.EstadoPedido;
import com.chitgo.pedidos.dominio.Pedido;
import com.chitgo.pedidos.dominio.puertos.PedidoRepository;
import com.chitgo.usuarios.dominio.puertos.EmisorDeHabilitaciones;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeDispositivo;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeSesion;

/**
 * HU-07 de punta a punta (CP-19-01 a CP-19-08 y CP-20-01): Caja entra con el PIN de verdad y los pedidos se arman
 * por el camino del cliente, con el QR de una mesa de la seed y el header X-Session-Token. Contra el MySQL del
 * Compose y R__datos_de_prueba.sql, sin {@code @Transactional}, por lo mismo que IngresoConPinTest: el choque de
 * CP-19-06 solo pasa con dos commits de verdad. Cada test borra lo que dejó.
 *
 * <p>Lo que viaja por el WebSocket no se mira acá: lo cubre ResolverPedidoServiceTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AprobacionDePedidosTest {

    private static final String COLA = "/api/v1/caja/pedidos";
    private static final String TOKEN_LA_ESQUINA = "83f39b25-deac-4fde-80cc-4cfdf8117713";
    private static final String MESA_1_ESQUINA = "d4964b6b-fdae-4b4f-83e9-1c1e78a21723";
    private static final String MESA_2_ESQUINA = "7ac79c59-b48d-4db2-8365-5c6a6565d9f4";
    private static final String MESA_1_PUERTO = "ab0124a8-6033-4d73-a1f6-3513140e62f7";
    private static final String SIN_PERMISO = "No tenés permiso para hacer esto.";
    /** Lo que se borra al terminar, en el orden de las FK. */
    private static final List<String> TABLAS_DE_LA_PRUEBA =
            List.of("registro_auditoria", "item_pedido", "pedido", "cliente_sesion", "aviso", "cuenta");

    /**
     * El PedidoRepository de verdad, que en CP-19-06 frena las dos primeras lecturas hasta que lleguen las dos.
     * Así las dos transacciones leen el pedido pendiente antes de que cualquiera guarde, y la segunda choca con
     * el @Version. Fuera de ese test no frena nada.
     */
    static class PedidosConBarrera implements PedidoRepository {

        record Lectura(EstadoPedido estado, Integer version) {
        }

        private final PedidoRepository real;
        private final AtomicInteger porFrenar = new AtomicInteger();
        private final List<Lectura> lecturas = new CopyOnWriteArrayList<>();
        private volatile CyclicBarrier barrera;

        PedidosConBarrera(PedidoRepository real) {
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
        public Optional<Pedido> buscarPorId(Long id, Long localId) {
            Optional<Pedido> pedido = real.buscarPorId(id, localId);
            if (porFrenar.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                pedido.ifPresent(p -> lecturas.add(new Lectura(p.getEstado(), p.getVersion())));
                esperarAlOtro();
            }
            return pedido;
        }

        /** Con tope: si el otro hilo no llega, el test falla en vez de colgarse. */
        private void esperarAlOtro() {
            try {
                barrera.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } catch (BrokenBarrierException | TimeoutException e) {
                throw new IllegalStateException("La otra aprobación no llegó a leer el pedido", e);
            }
        }

        @Override
        public Pedido guardar(Pedido pedido) {
            return real.guardar(pedido);
        }

        @Override
        public Optional<Pedido> buscarPorUuidCliente(String uuidCliente) {
            return real.buscarPorUuidCliente(uuidCliente);
        }

        @Override
        public List<Pedido> listarDeLaCuenta(Long cuentaId) {
            return real.listarDeLaCuenta(cuentaId);
        }

        @Override
        public List<Pedido> listarDeLasCuentas(Collection<Long> cuentaIds) {
            return real.listarDeLasCuentas(cuentaIds);
        }

        @Override
        public List<Pedido> listarActivosDeCocina(Long localId) {
            return real.listarActivosDeCocina(localId);
        }
    }

    @TestConfiguration
    static class ConRelojYBarrera {

        @Bean
        @Primary
        RelojDePrueba relojDePrueba() {
            // Sin fracciones de segundo, como guarda enviado_en.
            return new RelojDePrueba(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        }

        @Bean
        @Primary
        PedidosConBarrera pedidosConBarrera(@Qualifier("pedidoRepositoryAdapter") PedidoRepository real) {
            return new PedidosConBarrera(real);
        }
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
    private PedidosConBarrera pedidos;

    private final Map<String, Long> ultimoIdAntes = new HashMap<>();

    @BeforeEach
    void anotarLoQueHabia() {
        for (String tabla : TABLAS_DE_LA_PRUEBA) {
            ultimoIdAntes.put(tabla, jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM " + tabla, Long.class));
        }
    }

    @AfterEach
    void dejarLaBaseComoEnLaSeed() {
        pedidos.soltar();
        for (String tabla : TABLAS_DE_LA_PRUEBA) {
            jdbc.update("DELETE FROM " + tabla + " WHERE id > ?", ultimoIdAntes.get(tabla));
        }
        jdbc.update("UPDATE plato SET activo = TRUE");
    }

    // --- Aprobar y rechazar

    @Test
    void carlaApruebaYElPedidoLlegaACocina() throws Exception {
        // CP-19-01
        String cliente = abrirMesa(MESA_1_ESQUINA);
        Long pedido = pedir(cliente, plato("Milanesa napolitana", "La Esquina"), 2, "sin cebolla");
        Cookie carla = sesionDe("Carla", "2468");

        aprobar(carla, pedido).andExpect(status().isNoContent());

        Map<String, Object> fila = jdbc.queryForMap("""
                SELECT estado, resuelto_por_usuario_id, UNIX_TIMESTAMP(resuelto_en) AS resuelto_en, motivo_rechazo
                FROM pedido WHERE id = ?""", pedido);
        assertThat(fila.get("estado")).isEqualTo("APROBADO");
        assertThat(((Number) fila.get("resuelto_por_usuario_id")).longValue()).isEqualTo(idDe("Carla"));
        assertThat(((Number) fila.get("resuelto_en")).longValue()).isEqualTo(reloj.ahora().getEpochSecond());
        assertThat(fila.get("motivo_rechazo")).isNull();

        mvc.perform(get(COLA).cookie(carla))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(not(hasItem(pedido.intValue()))));
        mvc.perform(get("/api/v1/cocina/pedidos").cookie(sesionDe("Hugo", "1357")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enCola[*].id").value(hasItem(pedido.intValue())));
        mvc.perform(get("/api/v1/public/sesion/estado").header("X-Session-Token", cliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedidos[0].id").value(pedido))
                .andExpect(jsonPath("$.pedidos[0].estado").value("APROBADO"));

        Map<String, Object> registro = unicoRegistro();
        assertThat(registro.get("accion")).isEqualTo("APROBAR_PEDIDO");
        assertThat(registro.get("entidad")).isEqualTo("PEDIDO");
        assertThat(((Number) registro.get("entidad_id")).longValue()).isEqualTo(pedido);
        assertThat(((Number) registro.get("usuario_id")).longValue()).isEqualTo(idDe("Carla"));
        assertThat(registro.get("antes")).isEqualTo("PENDIENTE_APROBACION");
        assertThat(registro.get("despues")).isEqualTo("APROBADO");
    }

    @Test
    void carlaRechazaConMotivoYNoLlegaACocina() throws Exception {
        // CP-19-03. El version del cuerpo se ignora: el back lo toma de la base (21/10).
        String cliente = abrirMesa(MESA_1_ESQUINA);
        Long pedido = pedir(cliente, plato("Milanesa napolitana", "La Esquina"), 1, null);
        Cookie carla = sesionDe("Carla", "2468");

        rechazar(carla, pedido, """
                {"motivo": "MESA_NO_OCUPADA", "version": 7}""").andExpect(status().isNoContent());

        assertThat(jdbc.queryForMap("SELECT estado, motivo_rechazo FROM pedido WHERE id = ?", pedido))
                .containsEntry("estado", "RECHAZADO")
                .containsEntry("motivo_rechazo", "MESA_NO_OCUPADA");
        mvc.perform(get("/api/v1/public/sesion/estado").header("X-Session-Token", cliente))
                .andExpect(jsonPath("$.pedidos[0].estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.pedidos[0].motivoRechazo").value("MESA_NO_OCUPADA"));
        mvc.perform(get("/api/v1/cocina/pedidos").cookie(sesionDe("Hugo", "1357")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enCola[*].id").value(not(hasItem(pedido.intValue()))))
                .andExpect(jsonPath("$.enPreparacion[*].id").value(not(hasItem(pedido.intValue()))))
                .andExpect(jsonPath("$.listos[*].id").value(not(hasItem(pedido.intValue()))));

        Map<String, Object> registro = unicoRegistro();
        assertThat(registro.get("accion")).isEqualTo("RECHAZAR_PEDIDO");
        assertThat(registro.get("antes")).isEqualTo("PENDIENTE_APROBACION");
        assertThat(registro.get("despues")).isEqualTo("RECHAZADO");
        assertThat(registro.get("motivo")).isEqualTo("MESA_NO_OCUPADA");
    }

    @Test
    void rechazarSinMotivoDa400YSigueEnEspera() throws Exception {
        // CP-19-04, y un motivo que no está en MotivoRechazo
        String cliente = abrirMesa(MESA_1_ESQUINA);
        Long pedido = pedir(cliente, plato("Milanesa napolitana", "La Esquina"), 1, null);
        Cookie carla = sesionDe("Carla", "2468");

        for (String cuerpo : new String[] {"{}", "{\"motivo\": null}", "{\"motivo\": \"ME_CAYO_MAL\"}"}) {
            rechazar(carla, pedido, cuerpo)
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }

        assertThat(estadoDe(pedido)).isEqualTo("PENDIENTE_APROBACION");
        mvc.perform(get(COLA).cookie(carla)).andExpect(jsonPath("$[*].id").value(hasItem(pedido.intValue())));
        assertThat(registrosNuevos()).isZero();
    }

    @Test
    void siElClienteCanceloAntesAprobarDa409() throws Exception {
        // CP-19-05
        String cliente = abrirMesa(MESA_1_ESQUINA);
        Long pedido = pedir(cliente, plato("Milanesa napolitana", "La Esquina"), 1, null);
        Cookie carla = sesionDe("Carla", "2468");
        Cookie xsrf = xsrf();
        mvc.perform(post("/api/v1/public/pedidos/" + pedido + "/cancelar").cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue()).header("X-Session-Token", cliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"));

        aprobar(carla, pedido)
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("El pedido ya fue cancelado"));

        assertThat(estadoDe(pedido)).isEqualTo("CANCELADO");
        assertThat(registrosNuevos()).isZero();
    }

    @Test
    void dosCajasAprobandoALaVezSoloUnaGana() throws Exception {
        // CP-19-06: dos sesiones de Caja, dos hilos, y la barrera para que las dos lean antes de guardar.
        String cliente = abrirMesa(MESA_1_ESQUINA);
        Long pedido = pedir(cliente, plato("Milanesa napolitana", "La Esquina"), 1, null);
        Cookie primeraCaja = sesionDe("Carla", "2468");
        Cookie segundaCaja = sesionDe("Carla", "2468");

        pedidos.frenarLasProximasDosLecturas();
        List<MockHttpServletResponse> respuestas;
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            Future<MockHttpServletResponse> una = hilos.submit(() -> aprobar(primeraCaja, pedido).andReturn().getResponse());
            Future<MockHttpServletResponse> otra = hilos.submit(() -> aprobar(segundaCaja, pedido).andReturn().getResponse());
            respuestas = List.of(una.get(30, TimeUnit.SECONDS), otra.get(30, TimeUnit.SECONDS));
        } finally {
            hilos.shutdownNow();
        }

        // Las dos leyeron el pedido pendiente y con la misma versión: llegaron de verdad a la vez.
        assertThat(pedidos.lecturas()).containsExactly(
                new PedidosConBarrera.Lectura(EstadoPedido.PENDIENTE_APROBACION, 0),
                new PedidosConBarrera.Lectura(EstadoPedido.PENDIENTE_APROBACION, 0));
        assertThat(respuestas).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(204, 409);
        MockHttpServletResponse perdedora = respuestas.stream().filter(r -> r.getStatus() == 409).findFirst().orElseThrow();
        String detalle = JsonPath.read(perdedora.getContentAsString(StandardCharsets.UTF_8), "$.detail");
        assertThat(detalle).isEqualTo("El pedido ya fue resuelto");

        assertThat(estadoDe(pedido)).isEqualTo("APROBADO");
        assertThat(registrosNuevos()).isEqualTo(1);
    }

    // --- Quién puede

    @Test
    void elMozoNoPuedeAprobarNiRechazar() throws Exception {
        // CP-19-07: Juan manda el POST directo, sin pasar por ninguna pantalla.
        String cliente = abrirMesa(MESA_1_ESQUINA);
        Long pedido = pedir(cliente, plato("Milanesa napolitana", "La Esquina"), 1, null);
        Cookie juan = sesionDe("Juan", "1234");

        sinPermiso(aprobar(juan, pedido));
        sinPermiso(rechazar(juan, pedido, "{\"motivo\": \"MESA_NO_OCUPADA\"}"));

        assertThat(estadoDe(pedido)).isEqualTo("PENDIENTE_APROBACION");
        assertThat(registrosNuevos()).isZero();
    }

    @Test
    void unPedidoDeElPuertoDa404ComoSiNoExistiera() throws Exception {
        // CP-19-08 (RNF-10): la misma respuesta que un id que no existe.
        String cliente = abrirMesa(MESA_1_PUERTO);
        Long delPuerto = pedir(cliente, plato("Pizza muzzarella", "El Puerto"), 1, null);
        Cookie carla = sesionDe("Carla", "2468");

        String deOtroLocal = aprobar(carla, delPuerto)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El pedido no existe"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String queNoExiste = aprobar(carla, 999_999L)
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        rechazar(carla, delPuerto, "{\"motivo\": \"MESA_NO_OCUPADA\"}").andExpect(status().isNotFound());

        assertThat(JsonPath.<String>read(deOtroLocal, "$.detail")).isEqualTo(JsonPath.read(queNoExiste, "$.detail"));
        assertThat(deOtroLocal).doesNotContain("Puerto");
        assertThat(estadoDe(delPuerto)).isEqualTo("PENDIENTE_APROBACION");
        assertThat(registrosNuevos()).isZero();
    }

    @Test
    void aprobarSinElHeaderDeCsrfDa403() throws Exception {
        String cliente = abrirMesa(MESA_1_ESQUINA);
        Long pedido = pedir(cliente, plato("Milanesa napolitana", "La Esquina"), 1, null);

        mvc.perform(post(COLA + "/" + pedido + "/aprobar").cookie(sesionDe("Carla", "2468"), xsrf()))
                .andExpect(status().isForbidden());

        assertThat(estadoDe(pedido)).isEqualTo("PENDIENTE_APROBACION");
    }

    // --- La cola

    @Test
    void laColaTraeLosPendientesEnOrdenDeLlegadaConLaDisponibilidadYLaEspera() throws Exception {
        // CP-19-02, la parte del back: enviados a los 0, 2 y 5 minutos, y la cola abierta a los 5.
        Long milanesa = plato("Milanesa napolitana", "La Esquina");
        Long ensalada = plato("Ensalada mixta", "La Esquina");
        String mesa1 = abrirMesa(MESA_1_ESQUINA);
        String mesa2 = abrirMesa(MESA_2_ESQUINA);

        Long primero = pedir(mesa1, ensalada, 1, "sin sal");
        reloj.avanzar(Duration.ofMinutes(2));
        Long segundo = pedir(mesa2, milanesa, 2, null);
        reloj.avanzar(Duration.ofMinutes(3));
        Long tercero = pedir(mesa1, milanesa, 1, null);
        // HU-09 todavía no existe: la ensalada se apaga a mano, después de pedirla.
        jdbc.update("UPDATE plato SET activo = FALSE WHERE id = ?", ensalada);

        List<Map<String, Object>> cola = colaDeEstaPrueba(sesionDe("Carla", "2468"));

        assertThat(cola).extracting(p -> ((Number) p.get("id")).longValue()).containsExactly(primero, segundo, tercero);
        assertThat(cola).extracting(p -> p.get("mesa")).containsExactly("1", "2", "1");
        assertThat(cola).extracting(p -> ((Number) p.get("segundosDeEspera")).longValue()).containsExactly(300L, 180L, 0L);
        assertThat(cola).extracting(p -> p.get("demorado")).containsExactly(true, false, false);
        assertThat(cola.get(0).get("enviadoEn")).isEqualTo(reloj.ahora().minus(Duration.ofMinutes(5)).toString());
        assertThat(items(cola.get(0))).singleElement().satisfies(item -> assertThat(item)
                .containsEntry("plato", "Ensalada mixta")
                .containsEntry("cantidad", 1)
                .containsEntry("observacion", "sin sal")
                .containsEntry("disponible", false)
                .doesNotContainKey("precio"));
        assertThat(items(cola.get(1))).singleElement().satisfies(item -> assertThat(item)
                .containsEntry("plato", "Milanesa napolitana")
                .containsEntry("cantidad", 2)
                .containsEntry("disponible", true));
    }

    @Test
    void aLosTresMinutosYUnSegundoQuedaDemorado() throws Exception {
        // CP-20-01: enviado a las 20:00, a las 20:03:00 todavía no y a las 20:03:01 sí.
        String cliente = abrirMesa(MESA_1_ESQUINA);
        pedir(cliente, plato("Milanesa napolitana", "La Esquina"), 1, null);
        Cookie carla = sesionDe("Carla", "2468");

        reloj.avanzar(Duration.ofMinutes(3));
        assertThat(colaDeEstaPrueba(carla)).singleElement().satisfies(p -> assertThat(p)
                .containsEntry("segundosDeEspera", 180)
                .containsEntry("demorado", false));

        reloj.avanzar(Duration.ofSeconds(1));
        assertThat(colaDeEstaPrueba(carla)).singleElement().satisfies(p -> assertThat(p)
                .containsEntry("segundosDeEspera", 181)
                .containsEntry("demorado", true));
    }

    @Test
    void otroEstadoDa400() throws Exception {
        mvc.perform(get(COLA).param("estado", "APROBADO").cookie(sesionDe("Carla", "2468")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("La cola de Caja solo se pide con estado PENDIENTE_APROBACION"));
    }

    // --- Ayudas: el cliente

    /** El celular escanea el QR de una mesa libre y abre la cuenta: queda como iniciador. */
    private String abrirMesa(String tokenQr) throws Exception {
        Cookie xsrf = xsrf();
        String respuesta = mvc.perform(post("/api/v1/public/mesas/" + tokenQr + "/sesiones").cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.esIniciador").value(true))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(respuesta, "$.tokenSesion");
    }

    /** Manda un pedido de un plato con la sesión del iniciador. Sale con la hora del reloj de prueba. */
    private Long pedir(String tokenSesion, Long platoId, int cantidad, String observacion) throws Exception {
        Cookie xsrf = xsrf();
        String observacionJson = observacion == null ? "null" : "\"" + observacion + "\"";
        String cuerpo = """
                {"uuidCliente": "%s", "items": [{"platoId": %d, "cantidad": %d, "observacion": %s}]}"""
                .formatted(UUID.randomUUID(), platoId, cantidad, observacionJson);
        String respuesta = mvc.perform(post("/api/v1/public/pedidos").cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue()).header("X-Session-Token", tokenSesion)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE_APROBACION"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
    }

    // --- Ayudas: Caja

    private ResultActions aprobar(Cookie sesion, Long pedidoId) throws Exception {
        Cookie xsrf = xsrf();
        return mvc.perform(post(COLA + "/" + pedidoId + "/aprobar").cookie(sesion, xsrf)
                .header("X-XSRF-TOKEN", xsrf.getValue()));
    }

    private ResultActions rechazar(Cookie sesion, Long pedidoId, String cuerpo) throws Exception {
        Cookie xsrf = xsrf();
        return mvc.perform(post(COLA + "/" + pedidoId + "/rechazar").cookie(sesion, xsrf)
                .header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    /** La cola de Carla, solo con los pedidos que armó este test y en el orden en que vienen. */
    private List<Map<String, Object>> colaDeEstaPrueba(Cookie sesion) throws Exception {
        String respuesta = mvc.perform(get(COLA).cookie(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Map<String, Object>> cola = JsonPath.read(respuesta, "$");
        return cola.stream().filter(p -> ((Number) p.get("id")).longValue() > ultimoIdAntes.get("pedido")).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> pedido) {
        return (List<Map<String, Object>>) pedido.get("items");
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

    private String estadoDe(Long pedidoId) {
        return jdbc.queryForObject("SELECT estado FROM pedido WHERE id = ?", String.class, pedidoId);
    }

    private int registrosNuevos() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM registro_auditoria WHERE id > ?", Integer.class,
                ultimoIdAntes.get("registro_auditoria"));
    }

    /** El registro de auditoría que dejó el test, leído con SQL: así se ve el JSON que quedó en la columna. */
    private Map<String, Object> unicoRegistro() {
        assertThat(registrosNuevos()).isEqualTo(1);
        return jdbc.queryForMap("""
                SELECT usuario_id, accion, entidad, entidad_id, valor_anterior ->> '$.estado' AS antes,
                       valor_nuevo ->> '$.estado' AS despues, valor_nuevo ->> '$.motivo' AS motivo
                FROM registro_auditoria WHERE id > ?""", ultimoIdAntes.get("registro_auditoria"));
    }
}
