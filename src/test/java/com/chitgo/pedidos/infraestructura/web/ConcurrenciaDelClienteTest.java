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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
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

import com.chitgo.compartido.dominio.ConflictoDeConcurrenciaException;
import com.chitgo.compartido.dominio.RelojDePrueba;
import com.chitgo.pedidos.dominio.EstadoPedido;
import com.chitgo.pedidos.dominio.Mesa;
import com.chitgo.pedidos.dominio.Pedido;
import com.chitgo.pedidos.dominio.PedidoDuplicadoException;
import com.chitgo.pedidos.dominio.puertos.MesaRepository;
import com.chitgo.pedidos.dominio.puertos.PedidoRepository;
import com.chitgo.usuarios.dominio.puertos.EmisorDeHabilitaciones;

/**
 * Los choques del cliente (ST-05, tarea 2): dos escaneos de la misma mesa a la vez (CP-01-06), el mismo pedido
 * enviado dos veces (CP-03-04) y Caja aprobando mientras el cliente cancela. También las cancelaciones que no
 * chocan: un pedido en preparación (CP-05-05) y el cancelado que sale de la cola de Caja (CP-05-01).
 *
 * <p>Como AprobacionDePedidosTest: contra el MySQL del Compose y R__datos_de_prueba.sql, sin {@code @Transactional},
 * porque cada choque necesita dos transacciones de verdad, y cada test borra lo que dejó. Cada choque se fuerza con
 * un freno en el repositorio, y el test mira qué leyó cada operación, no solo las respuestas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ConcurrenciaDelClienteTest {

    private static final String COLA = "/api/v1/caja/pedidos";
    private static final String TOKEN_LA_ESQUINA = "83f39b25-deac-4fde-80cc-4cfdf8117713";
    private static final String MESA_1_ESQUINA = "d4964b6b-fdae-4b4f-83e9-1c1e78a21723";
    private static final String MESA_2_ESQUINA = "7ac79c59-b48d-4db2-8365-5c6a6565d9f4";
    /** Las cookies van por nombre, sin importar nada de usuarios/infraestructura/seguridad (regla de ST-05). */
    private static final String COOKIE_DE_SESION = "chitgo_sesion";
    private static final String COOKIE_DE_DISPOSITIVO = "chitgo_dispositivo";
    /** Lo que se borra al terminar, en el orden de las FK. */
    private static final List<String> TABLAS_DE_LA_PRUEBA =
            List.of("registro_auditoria", "item_pedido", "pedido", "cliente_sesion", "aviso", "cuenta");

    /**
     * El PedidoRepository de verdad, con dos frenos que solo se prenden en los tests de choque. Uno frena las dos
     * primeras lecturas, por id o por uuidCliente, hasta que lleguen las dos, como en CP-19-06. El otro ordena los
     * guardar: el que trae el pedido en otro estado espera a que vuelva el guardar del que tiene que ganar.
     */
    static class PedidosConBarrera implements PedidoRepository {

        /** Lo que vio una lectura frenada: el estado y la versión, o NINGUNO si no encontró el pedido. */
        record Lectura(EstadoPedido estado, Integer version) {

            static final Lectura NINGUNO = new Lectura(null, null);

            static Lectura de(Optional<Pedido> pedido) {
                return pedido.map(p -> new Lectura(p.getEstado(), p.getVersion())).orElse(NINGUNO);
            }
        }

        /** Un guardar que falló: contra qué chocó (el UNIQUE de uuid_cliente o el @Version) y cómo traía el pedido. */
        record Choque(String contra, EstadoPedido estado) {
        }

        private final PedidoRepository real;
        private final AtomicInteger porFrenar = new AtomicInteger();
        private final List<Lectura> lecturas = new CopyOnWriteArrayList<>();
        private final List<Choque> choques = new CopyOnWriteArrayList<>();
        private volatile CyclicBarrier barrera;
        private volatile EstadoPedido ganador;
        private volatile CountDownLatch guardoElGanador;

        PedidosConBarrera(PedidoRepository real) {
            this.real = real;
        }

        void frenarLasProximasDosLecturas() {
            lecturas.clear();
            choques.clear();
            barrera = new CyclicBarrier(2);
            porFrenar.set(2);
        }

        /** Guarda primero el que trae el pedido en ese estado; el otro espera a que vuelva ese guardar. */
        void queGuardePrimero(EstadoPedido estado) {
            guardoElGanador = new CountDownLatch(1);
            ganador = estado;
        }

        void soltar() {
            porFrenar.set(0);
            ganador = null;
            lecturas.clear();
            choques.clear();
        }

        List<Lectura> lecturas() {
            return List.copyOf(lecturas);
        }

        List<Choque> choques() {
            return List.copyOf(choques);
        }

        @Override
        public Optional<Pedido> buscarPorId(Long id, Long localId) {
            return frenarSiToca(real.buscarPorId(id, localId));
        }

        @Override
        public Optional<Pedido> buscarPorUuidCliente(String uuidCliente) {
            return frenarSiToca(real.buscarPorUuidCliente(uuidCliente));
        }

        private Optional<Pedido> frenarSiToca(Optional<Pedido> pedido) {
            if (porFrenar.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                lecturas.add(Lectura.de(pedido));
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
                throw new IllegalStateException("La otra operación no llegó a leer el pedido", e);
            }
        }

        @Override
        public Pedido guardar(Pedido pedido) {
            EstadoPedido primero = ganador;
            boolean esElGanador = primero != null && pedido.getEstado() == primero;
            if (primero != null && !esElGanador) {
                // El ganador ya hizo el UPDATE y tiene la fila: este UPDATE espera su commit y no toca nada.
                esperar(guardoElGanador, "El que tenía que ganar no llegó a guardar");
            }
            try {
                return real.guardar(pedido);
            } catch (PedidoDuplicadoException e) {
                choques.add(new Choque("UNIQUE", pedido.getEstado()));
                throw e;
            } catch (ConflictoDeConcurrenciaException e) {
                choques.add(new Choque("@Version", pedido.getEstado()));
                throw e;
            } finally {
                if (esElGanador) {
                    guardoElGanador.countDown();
                }
            }
        }

        @Override
        public List<Pedido> listarDeLaCuenta(Long cuentaId) {
            return real.listarDeLaCuenta(cuentaId);
        }

        @Override
        public List<Pedido> listarActivosDeCocina(Long localId) {
            return real.listarActivosDeCocina(localId);
        }
    }

    /**
     * El MesaRepository de verdad, que en CP-01-06 frena al primer escaneo con la fila de la mesa ya bloqueada, hasta
     * que el test lo suelte. Anota cada paso, para que el test vea en qué orden pasaron. Fuera de ese test no frena.
     */
    static class MesasConFreno implements MesaRepository {

        private final MesaRepository real;
        private final List<String> pasos = new CopyOnWriteArrayList<>();
        private final AtomicBoolean primeroPorFrenar = new AtomicBoolean();
        private volatile boolean activo;
        private volatile CountDownLatch tomoLaMesa = new CountDownLatch(0);
        private volatile CountDownLatch seguir = new CountDownLatch(0);

        MesasConFreno(MesaRepository real) {
            this.real = real;
        }

        void frenarAlPrimerEscaneo() {
            pasos.clear();
            tomoLaMesa = new CountDownLatch(1);
            seguir = new CountDownLatch(1);
            primeroPorFrenar.set(true);
            activo = true;
        }

        void esperarQueElPrimeroTomeLaMesa() {
            esperar(tomoLaMesa, "El primer escaneo no llegó a bloquear la mesa");
        }

        void soltarAlPrimero() {
            seguir.countDown();
        }

        void soltar() {
            activo = false;
            seguir.countDown();
        }

        List<String> pasos() {
            return List.copyOf(pasos);
        }

        @Override
        public Optional<Mesa> buscarPorTokenQr(String tokenQr) {
            return real.buscarPorTokenQr(tokenQr);
        }

        @Override
        public Optional<Mesa> buscarPorTokenQrParaAbrirCuenta(String tokenQr) {
            if (!activo) {
                return real.buscarPorTokenQrParaAbrirCuenta(tokenQr);
            }
            if (primeroPorFrenar.getAndSet(false)) {
                Optional<Mesa> mesa = real.buscarPorTokenQrParaAbrirCuenta(tokenQr);
                pasos.add("A tomó la mesa");
                tomoLaMesa.countDown();
                esperar(seguir, "El test no soltó al primer escaneo");
                pasos.add("A sigue");
                return mesa;
            }
            pasos.add("B pide la mesa");
            Optional<Mesa> mesa = real.buscarPorTokenQrParaAbrirCuenta(tokenQr);
            pasos.add("B tomó la mesa");
            return mesa;
        }
    }

    @TestConfiguration
    static class ConRelojYFrenos {

        @Bean
        @Primary
        RelojDePrueba relojDePrueba() {
            // Sin fracciones de segundo, como guarda cancelado_en.
            return new RelojDePrueba(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        }

        @Bean
        @Primary
        PedidosConBarrera pedidosConBarrera(@Qualifier("pedidoRepositoryAdapter") PedidoRepository real) {
            return new PedidosConBarrera(real);
        }

        @Bean
        @Primary
        MesasConFreno mesasConFreno(@Qualifier("mesaRepositoryAdapter") MesaRepository real) {
            return new MesasConFreno(real);
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

    @Autowired
    private MesasConFreno mesas;

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
        mesas.soltar();
        for (String tabla : TABLAS_DE_LA_PRUEBA) {
            jdbc.update("DELETE FROM " + tabla + " WHERE id > ?", ultimoIdAntes.get(tabla));
        }
    }

    // --- Dos escaneos a la vez

    @Test
    void dosEscaneosALaVezAbrenUnaSolaCuenta() throws Exception {
        // CP-01-06: A bloquea la fila de la mesa 2 y queda frenada; recién ahí escanea B, que tiene que esperar.
        mesas.frenarAlPrimerEscaneo();
        List<MockHttpServletResponse> respuestas;
        boolean elSegundoEsperoElBloqueo;
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            Future<MockHttpServletResponse> primero = hilos.submit(() -> escanear(MESA_2_ESQUINA).andReturn().getResponse());
            mesas.esperarQueElPrimeroTomeLaMesa();
            Future<MockHttpServletResponse> segundo = hilos.submit(() -> escanear(MESA_2_ESQUINA).andReturn().getResponse());
            elSegundoEsperoElBloqueo = hayUnForUpdateDeMesaEsperando();
            mesas.soltarAlPrimero();
            respuestas = List.of(primero.get(30, TimeUnit.SECONDS), segundo.get(30, TimeUnit.SECONDS));
        } finally {
            mesas.soltarAlPrimero();
            hilos.shutdownNow();
        }

        // B estuvo en MySQL esperando el FOR UPDATE mientras A tenía la mesa, y la tomó recién después.
        assertThat(elSegundoEsperoElBloqueo).as("el SELECT ... FOR UPDATE de B en PROCESSLIST").isTrue();
        assertThat(mesas.pasos()).containsExactly("A tomó la mesa", "B pide la mesa", "A sigue", "B tomó la mesa");

        assertThat(respuestas).extracting(MockHttpServletResponse::getStatus).containsExactly(201, 201);
        String primero = cuerpo(respuestas.get(0));
        String segundo = cuerpo(respuestas.get(1));
        assertThat(JsonPath.<Boolean>read(primero, "$.esIniciador")).isTrue();
        assertThat(JsonPath.<Boolean>read(segundo, "$.esIniciador")).isFalse();

        List<Long> cuentas = jdbc.queryForList("SELECT id FROM cuenta WHERE mesa_id = ? AND estado <> 'CERRADA'",
                Long.class, mesaDe(MESA_2_ESQUINA));
        assertThat(cuentas).hasSize(1);
        List<Map<String, Object>> sesiones = jdbc.queryForList(
                "SELECT token_sesion, es_iniciador FROM cliente_sesion WHERE cuenta_id = ? ORDER BY id", cuentas.get(0));
        assertThat(sesiones).extracting(s -> s.get("token_sesion"))
                .containsExactly(JsonPath.read(primero, "$.tokenSesion"), JsonPath.read(segundo, "$.tokenSesion"));
        assertThat(sesiones).extracting(s -> s.get("es_iniciador")).containsExactly(true, false);
    }

    // --- El mismo pedido dos veces

    @Test
    void reenviarElMismoPedidoDevuelveElQueYaExiste() throws Exception {
        // CP-03-04 en secuencia: el reintento de la app, con el mismo uuidCliente.
        String cliente = abrirMesa(MESA_1_ESQUINA);
        UUID uuid = UUID.randomUUID();
        String pedido = cuerpoDePedido(uuid, plato("Milanesa napolitana"));

        MockHttpServletResponse primera = enviar(cliente, pedido).andReturn().getResponse();
        MockHttpServletResponse segunda = enviar(cliente, pedido).andReturn().getResponse();

        assertThat(primera.getStatus()).isEqualTo(201);
        assertThat(segunda.getStatus()).isEqualTo(200);
        assertThat(cuerpo(segunda)).isEqualTo(cuerpo(primera));
        Long id = idEn(primera);
        assertThat(pedidosConUuid(uuid)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM item_pedido WHERE pedido_id = ?", Integer.class, id))
                .isEqualTo(1);
    }

    @Test
    void dosEnviosIgualesALaVezDejanUnSoloPedido() throws Exception {
        // CP-03-04 a la vez: los dos pasan el control de idempotencia antes de que cualquiera inserte.
        String cliente = abrirMesa(MESA_1_ESQUINA);
        UUID uuid = UUID.randomUUID();
        String pedido = cuerpoDePedido(uuid, plato("Milanesa napolitana"));

        pedidos.frenarLasProximasDosLecturas();
        List<MockHttpServletResponse> respuestas = aLaVez(
                () -> enviar(cliente, pedido).andReturn().getResponse(),
                () -> enviar(cliente, pedido).andReturn().getResponse());

        // Ninguno encontró el pedido del otro: el que perdió chocó con el UNIQUE de uuid_cliente al insertar.
        assertThat(pedidos.lecturas()).containsExactly(
                PedidosConBarrera.Lectura.NINGUNO, PedidosConBarrera.Lectura.NINGUNO);
        assertThat(pedidos.choques()).containsExactly(
                new PedidosConBarrera.Choque("UNIQUE", EstadoPedido.PENDIENTE_APROBACION));

        assertThat(respuestas).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(201, 200);
        assertThat(cuerpo(respuestas.get(0))).isEqualTo(cuerpo(respuestas.get(1)));
        assertThat(pedidosConUuid(uuid)).isEqualTo(1);
    }

    // --- Caja aprueba mientras el cliente cancela

    @Test
    void siCajaApruebaPrimeroElClienteRecibe409() throws Exception {
        String cliente = abrirMesa(MESA_1_ESQUINA);
        Long pedido = pedir(cliente, plato("Milanesa napolitana"));
        Cookie carla = sesionDe("Carla", "2468");

        pedidos.frenarLasProximasDosLecturas();
        pedidos.queGuardePrimero(EstadoPedido.APROBADO);
        List<MockHttpServletResponse> respuestas = aLaVez(
                () -> aprobar(carla, pedido).andReturn().getResponse(),
                () -> cancelar(cliente, pedido).andReturn().getResponse());

        // Las dos leyeron el pedido pendiente y con la misma versión; la cancelación chocó con el @Version.
        assertThat(pedidos.lecturas()).containsExactly(
                new PedidosConBarrera.Lectura(EstadoPedido.PENDIENTE_APROBACION, 0),
                new PedidosConBarrera.Lectura(EstadoPedido.PENDIENTE_APROBACION, 0));
        assertThat(pedidos.choques()).containsExactly(new PedidosConBarrera.Choque("@Version", EstadoPedido.CANCELADO));

        MockHttpServletResponse deCarla = respuestas.get(0);
        MockHttpServletResponse delCliente = respuestas.get(1);
        assertThat(deCarla.getStatus()).isEqualTo(204);
        assertThat(delCliente.getStatus()).isEqualTo(409);
        assertThat(detalle(delCliente)).isEqualTo("El pedido ya cambió de estado. Si necesitás cambiarlo, llamá al mozo.");

        assertThat(estadoDe(pedido)).isEqualTo("APROBADO");
        assertThat(registrosNuevos()).isEqualTo(1);
        Map<String, Object> registro = jdbc.queryForMap("SELECT accion, entidad_id FROM registro_auditoria WHERE id > ?",
                ultimoIdAntes.get("registro_auditoria"));
        assertThat(registro.get("accion")).isEqualTo("APROBAR_PEDIDO");
        assertThat(((Number) registro.get("entidad_id")).longValue()).isEqualTo(pedido);
    }

    @Test
    void siElClienteCancelaPrimeroCajaRecibe409() throws Exception {
        String cliente = abrirMesa(MESA_1_ESQUINA);
        Long pedido = pedir(cliente, plato("Milanesa napolitana"));
        Cookie carla = sesionDe("Carla", "2468");

        pedidos.frenarLasProximasDosLecturas();
        pedidos.queGuardePrimero(EstadoPedido.CANCELADO);
        List<MockHttpServletResponse> respuestas = aLaVez(
                () -> aprobar(carla, pedido).andReturn().getResponse(),
                () -> cancelar(cliente, pedido).andReturn().getResponse());

        // Las dos leyeron el pedido pendiente y con la misma versión; la aprobación chocó con el @Version.
        assertThat(pedidos.lecturas()).containsExactly(
                new PedidosConBarrera.Lectura(EstadoPedido.PENDIENTE_APROBACION, 0),
                new PedidosConBarrera.Lectura(EstadoPedido.PENDIENTE_APROBACION, 0));
        assertThat(pedidos.choques()).containsExactly(new PedidosConBarrera.Choque("@Version", EstadoPedido.APROBADO));

        MockHttpServletResponse deCarla = respuestas.get(0);
        MockHttpServletResponse delCliente = respuestas.get(1);
        assertThat(delCliente.getStatus()).isEqualTo(200);
        assertThat(JsonPath.<String>read(cuerpo(delCliente), "$.estado")).isEqualTo("CANCELADO");
        assertThat(deCarla.getStatus()).isEqualTo(409);
        assertThat(detalle(deCarla)).isEqualTo("El pedido ya fue cancelado");

        Map<String, Object> fila = jdbc.queryForMap("""
                SELECT estado, UNIX_TIMESTAMP(cancelado_en) AS cancelado_en, cancelado_por_usuario_id,
                       resuelto_por_usuario_id
                FROM pedido WHERE id = ?""", pedido);
        assertThat(fila.get("estado")).isEqualTo("CANCELADO");
        assertThat(((Number) fila.get("cancelado_en")).longValue()).isEqualTo(reloj.ahora().getEpochSecond());
        assertThat(fila.get("cancelado_por_usuario_id")).isNull();
        assertThat(fila.get("resuelto_por_usuario_id")).isNull();
        // Lo del cliente no se audita (HU-15), y la aprobación que perdió no llegó a auditar.
        assertThat(registrosNuevos()).isZero();
    }

    // --- Cancelar

    @Test
    void unPedidoEnPreparacionNoSeCancela() throws Exception {
        // CP-05-05: Caja no tiene cómo cancelar en el Sprint 1, así que lo intenta el cliente (tarea 1 de ST-05).
        String cliente = abrirMesa(MESA_1_ESQUINA);
        Long pedido = pedir(cliente, plato("Milanesa napolitana"));
        aprobar(sesionDe("Carla", "2468"), pedido).andExpect(status().isNoContent());
        Cookie hugo = sesionDe("Hugo", "1357");
        Cookie xsrf = xsrf();
        mvc.perform(post("/api/v1/cocina/pedidos/" + pedido + "/en-preparacion").cookie(hugo, xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isNoContent());

        cancelar(cliente, pedido)
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("El pedido ya se está preparando y no se puede cancelar"));

        // Ni el estado ni la versión cambiaron: aprobar y en preparación la dejaron en 2.
        assertThat(jdbc.queryForMap("SELECT estado, cancelado_en, version FROM pedido WHERE id = ?", pedido))
                .containsEntry("estado", "EN_PREPARACION")
                .containsEntry("cancelado_en", null)
                .containsEntry("version", 2);
        mvc.perform(get("/api/v1/cocina/pedidos").cookie(hugo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enPreparacion[*].id").value(hasItem(pedido.intValue())));
    }

    @Test
    void elPedidoCanceladoSaleDeLaColaDeCarla() throws Exception {
        // CP-05-01, lo que faltaba (tarea 1 de ST-05): el pedido estaba en la cola de Caja y después de cancelarlo ya no.
        String cliente = abrirMesa(MESA_1_ESQUINA);
        Long pedido = pedir(cliente, plato("Milanesa napolitana"));
        Cookie carla = sesionDe("Carla", "2468");
        mvc.perform(get(COLA).cookie(carla)).andExpect(jsonPath("$[*].id").value(hasItem(pedido.intValue())));

        cancelar(cliente, pedido)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"));

        mvc.perform(get(COLA).cookie(carla))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(not(hasItem(pedido.intValue()))));
    }

    // --- Ayudas: los choques

    /** Las dos en dos hilos a la vez, con tope. Las respuestas, en el orden en que se pasaron. */
    private static List<MockHttpServletResponse> aLaVez(Callable<MockHttpServletResponse> una,
            Callable<MockHttpServletResponse> otra) throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            Future<MockHttpServletResponse> primera = hilos.submit(una);
            Future<MockHttpServletResponse> segunda = hilos.submit(otra);
            return List.of(primera.get(30, TimeUnit.SECONDS), segunda.get(30, TimeUnit.SECONDS));
        } finally {
            hilos.shutdownNow();
        }
    }

    /**
     * Si en 5 segundos aparece otra conexión de la app parada en el SELECT ... FOR UPDATE de una mesa. El usuario
     * chitgo ve sus propios hilos sin el permiso PROCESS (probado el 26/10). Se deja afuera la conexión de esta
     * consulta, porque su texto también dice "for update".
     */
    private boolean hayUnForUpdateDeMesaEsperando() throws InterruptedException {
        long tope = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < tope) {
            Integer esperando = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.PROCESSLIST
                    WHERE ID <> CONNECTION_ID() AND COMMAND = 'Query' AND INFO LIKE '%from mesa%for update%'""",
                    Integer.class);
            if (esperando > 0) {
                return true;
            }
            Thread.sleep(20);
        }
        return false;
    }

    /** Con tope: si la otra parte no llega, el test falla en vez de colgarse. */
    private static void esperar(CountDownLatch senal, String siNoLlega) {
        try {
            if (!senal.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(siNoLlega);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    // --- Ayudas: el cliente

    /** Un celular nuevo escanea el QR: sin X-Session-Token. */
    private ResultActions escanear(String tokenQr) throws Exception {
        Cookie xsrf = xsrf();
        return mvc.perform(post("/api/v1/public/mesas/" + tokenQr + "/sesiones").cookie(xsrf)
                .header("X-XSRF-TOKEN", xsrf.getValue()));
    }

    /** El celular escanea el QR de una mesa libre y abre la cuenta: queda como iniciador. */
    private String abrirMesa(String tokenQr) throws Exception {
        String respuesta = escanear(tokenQr)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.esIniciador").value(true))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(respuesta, "$.tokenSesion");
    }

    private static String cuerpoDePedido(UUID uuidCliente, Long platoId) {
        return """
                {"uuidCliente": "%s", "items": [{"platoId": %d, "cantidad": 1, "observacion": null}]}"""
                .formatted(uuidCliente, platoId);
    }

    private ResultActions enviar(String tokenSesion, String cuerpo) throws Exception {
        Cookie xsrf = xsrf();
        return mvc.perform(post("/api/v1/public/pedidos").cookie(xsrf)
                .header("X-XSRF-TOKEN", xsrf.getValue()).header("X-Session-Token", tokenSesion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    /** Manda un pedido de un plato con la sesión del iniciador. */
    private Long pedir(String tokenSesion, Long platoId) throws Exception {
        String respuesta = enviar(tokenSesion, cuerpoDePedido(UUID.randomUUID(), platoId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE_APROBACION"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
    }

    private ResultActions cancelar(String tokenSesion, Long pedidoId) throws Exception {
        Cookie xsrf = xsrf();
        return mvc.perform(post("/api/v1/public/pedidos/" + pedidoId + "/cancelar").cookie(xsrf)
                .header("X-XSRF-TOKEN", xsrf.getValue()).header("X-Session-Token", tokenSesion));
    }

    // --- Ayudas: el personal

    private ResultActions aprobar(Cookie sesion, Long pedidoId) throws Exception {
        Cookie xsrf = xsrf();
        return mvc.perform(post(COLA + "/" + pedidoId + "/aprobar").cookie(sesion, xsrf)
                .header("X-XSRF-TOKEN", xsrf.getValue()));
    }

    /** Entra con el PIN como el front, con la cookie de dispositivo del usuario, como en AprobacionDePedidosTest. */
    private Cookie sesionDe(String nombre, String pin) throws Exception {
        Long usuarioId = idDe(nombre);
        Cookie xsrf = xsrf();
        Cookie dispositivo = new Cookie(COOKIE_DE_DISPOSITIVO, habilitaciones.emitir(usuarioId));
        String cuerpo = """
                {"tokenStaff": "%s", "usuarioId": %d, "pin": "%s"}""".formatted(TOKEN_LA_ESQUINA, usuarioId, pin);
        Cookie sesion = mvc.perform(post("/api/v1/auth/pin").cookie(xsrf, dispositivo)
                        .header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(COOKIE_DE_SESION);
        assertThat(sesion).isNotNull();
        return sesion;
    }

    private Cookie xsrf() throws Exception {
        return mvc.perform(get("/api/v1/ping")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    // --- Ayudas: las respuestas

    private static String cuerpo(MockHttpServletResponse respuesta) throws Exception {
        return respuesta.getContentAsString(StandardCharsets.UTF_8);
    }

    private static Long idEn(MockHttpServletResponse respuesta) throws Exception {
        return ((Number) JsonPath.read(cuerpo(respuesta), "$.id")).longValue();
    }

    private static String detalle(MockHttpServletResponse respuesta) throws Exception {
        return JsonPath.read(cuerpo(respuesta), "$.detail");
    }

    // --- Ayudas: la base

    private Long idDe(String nombre) {
        return jdbc.queryForObject("""
                SELECT u.id FROM usuario u JOIN local l ON l.id = u.local_id
                WHERE u.nombre = ? AND l.nombre = 'La Esquina'""", Long.class, nombre);
    }

    private Long plato(String nombre) {
        return jdbc.queryForObject("""
                SELECT p.id FROM plato p JOIN local l ON l.id = p.local_id
                WHERE p.nombre = ? AND l.nombre = 'La Esquina'""", Long.class, nombre);
    }

    private Long mesaDe(String tokenQr) {
        return jdbc.queryForObject("SELECT id FROM mesa WHERE token_qr = ?", Long.class, tokenQr);
    }

    private String estadoDe(Long pedidoId) {
        return jdbc.queryForObject("SELECT estado FROM pedido WHERE id = ?", String.class, pedidoId);
    }

    private int pedidosConUuid(UUID uuidCliente) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM pedido WHERE uuid_cliente = ?", Integer.class,
                uuidCliente.toString());
    }

    private int registrosNuevos() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM registro_auditoria WHERE id > ?", Integer.class,
                ultimoIdAntes.get("registro_auditoria"));
    }
}
