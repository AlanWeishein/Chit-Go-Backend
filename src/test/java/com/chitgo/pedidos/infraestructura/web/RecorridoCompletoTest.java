package com.chitgo.pedidos.infraestructura.web;

import static com.chitgo.pruebas.AyudasDeIntegracion.COOKIE_DE_SESION;
import static com.chitgo.pruebas.AyudasDeIntegracion.MESA_1_ESQUINA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.chitgo.compartido.dominio.RelojDePrueba;
import com.chitgo.pruebas.AyudasDeIntegracion;
import com.chitgo.usuarios.dominio.puertos.EmisorDeHabilitaciones;

/**
 * El objetivo del Sprint 1 de punta a punta en el back (ST-05, tarea 3): el celular escanea y pide, Caja aprueba,
 * Cocina lo prepara y el mozo se entera, mirando también lo que viaja por STOMP (CP-01-01, CP-03-01, CP-19-01,
 * CP-19-03 y CP-24-01). Suma los casos por HTTP que la tarea 1 dejó para esta clase: CP-01-03, CP-03-08 y CP-24-03,
 * y CP-02-02, el filtro de la carta, que se sumó al cerrar la ST (tarea 4).
 *
 * <p>El HTTP va por MockMvc y /ws por el puerto real, con el mismo contexto y el mismo broker. Como
 * AprobacionDePedidosTest: contra el MySQL del Compose y R__datos_de_prueba.sql, sin {@code @Transactional}, y cada
 * test borra lo que dejó.
 *
 * <p>El broker simple no confirma el SUBSCRIBE (no contesta RECEIPT) y lo registra en otro hilo: antes de empezar,
 * a cada suscripción se le mandan sondas hasta que le llega una. Las sondas no cuentan como eventos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class RecorridoCompletoTest {

    private static final Duration TOPE = Duration.ofSeconds(5);
    private static final String SONDA = "sonda:";
    /** Lo que trae EventoTiempoReal: sin precios ni datos del personal (RF-31). */
    private static final Set<String> CAMPOS_DEL_EVENTO =
            Set.of("tipo", "pedidoId", "mesaId", "estado", "detalle", "ocurridoEn");

    @TestConfiguration
    static class ConReloj {

        @Bean
        @Primary
        RelojDePrueba relojDePrueba() {
            // Sin fracciones de segundo, como guarda la base.
            return new RelojDePrueba(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        }
    }

    /** Un evento de tiempo real, leído del JSON del frame. */
    record Evento(String tipo, Long pedidoId, Long mesaId, String estado, String detalle, String ocurridoEn) {
    }

    /**
     * Una conexión STOMP de prueba con una suscripción. Junta en una cola lo que le llega, y en otra el mensaje de
     * los frames ERROR. Sabe mandarse una sonda a su propio destino.
     */
    static final class Escucha extends StompSessionHandlerAdapter {

        final BlockingQueue<String> mensajes = new LinkedBlockingQueue<>();
        final BlockingQueue<String> errores = new LinkedBlockingQueue<>();
        StompSession sesion;
        Consumer<String> sonda;

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return byte[].class;
        }

        /** A la sesión solo le llegan los ERROR: los MESSAGE van a la suscripción. */
        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            errores.add(String.valueOf(headers.getFirst("message")));
        }

        void suscribir(String destino, Consumer<String> sonda) {
            this.sonda = sonda;
            sesion.subscribe(destino, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return byte[].class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    mensajes.add(new String((byte[]) payload, StandardCharsets.UTF_8));
                }
            });
        }
    }

    @LocalServerPort
    private int puerto;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EmisorDeHabilitaciones habilitaciones;

    @Autowired
    private RelojDePrueba reloj;

    @Autowired
    private SimpMessagingTemplate plantilla;

    private AyudasDeIntegracion ayudas;
    private WebSocketStompClient stomp;
    private final List<StompSession> abiertas = new CopyOnWriteArrayList<>();

    @BeforeEach
    void anotarLoQueHabia() {
        ayudas = new AyudasDeIntegracion(mvc, jdbc, habilitaciones);
        ayudas.anotarLoQueHabia();
        stomp = new WebSocketStompClient(new StandardWebSocketClient());
    }

    @AfterEach
    void cerrarYDejarLaBaseComoEnLaSeed() {
        for (StompSession sesion : abiertas) {
            if (sesion.isConnected()) {
                sesion.disconnect();
            }
        }
        stomp.stop();
        ayudas.borrarLoQueDejo();
    }

    // --- El recorrido

    @Test
    void elPedidoVaDelCelularACajaYACocinaYCadaUnoSeEnteraPorStomp() throws Exception {
        Long mesa = ayudas.mesaDe(MESA_1_ESQUINA);
        Long milanesa = ayudas.plato("Milanesa napolitana", "La Esquina");
        Long flan = ayudas.plato("Flan casero", "La Esquina");
        Cookie carla = ayudas.sesionDe("Carla", "2468");
        Cookie hugo = ayudas.sesionDe("Hugo", "1357");
        Cookie juan = ayudas.sesionDe("Juan", "1234");
        Escucha caja = escucharAlPersonal(carla, "caja/pedidos");
        Escucha cocina = escucharAlPersonal(hugo, "cocina/cola");
        Escucha mozo = escucharAlPersonal(juan, "mozo/avisos");
        Instant inicio = reloj.ahora();

        // CP-01-01: A escanea la mesa 1, que no tiene cuenta, y la abre como iniciador.
        String a = ayudas.abrirMesa(MESA_1_ESQUINA);
        Map<String, Object> sesionDeA = jdbc.queryForMap("""
                SELECT s.id, s.es_iniciador, c.id AS cuenta_id, c.mesa_id, c.estado
                FROM cliente_sesion s JOIN cuenta c ON c.id = s.cuenta_id WHERE s.token_sesion = ?""", a);
        assertThat(sesionDeA).containsEntry("es_iniciador", true).containsEntry("estado", "ABIERTA");
        assertThat(((Number) sesionDeA.get("mesa_id")).longValue()).isEqualTo(mesa);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cuenta WHERE mesa_id = ? AND estado = 'ABIERTA'",
                Integer.class, mesa)).isEqualTo(1);
        Escucha celular = escucharAlCliente(a);

        // CP-03-01: 2 Milanesas "sin huevo" y 1 Flan. Carla se entera.
        reloj.avanzar(Duration.ofMinutes(1));
        String cuerpo = """
                {"uuidCliente": "%s", "items": [
                  {"platoId": %d, "cantidad": 2, "observacion": "sin huevo"},
                  {"platoId": %d, "cantidad": 1, "observacion": null}]}""".formatted(UUID.randomUUID(), milanesa, flan);
        String respuesta = ayudas.enviar(a, cuerpo)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE_APROBACION"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Long pedido = ((Number) JsonPath.read(respuesta, "$.id")).longValue();

        assertThat(evento(caja)).isEqualTo(new Evento("PEDIDO_ENVIADO", pedido, mesa, "PENDIENTE_APROBACION", null, ahora()));
        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT estado, cuenta_id, cliente_sesion_id FROM pedido WHERE id = ?", pedido);
        assertThat(fila.get("estado")).isEqualTo("PENDIENTE_APROBACION");
        assertThat(((Number) fila.get("cuenta_id")).longValue()).isEqualTo(((Number) sesionDeA.get("cuenta_id")).longValue());
        assertThat(((Number) fila.get("cliente_sesion_id")).longValue()).isEqualTo(((Number) sesionDeA.get("id")).longValue());
        assertThat(jdbc.queryForList("""
                SELECT p.nombre AS plato, i.cantidad, CAST(i.precio_unitario AS CHAR) AS precio, i.observacion
                FROM item_pedido i JOIN plato p ON p.id = i.plato_id WHERE i.pedido_id = ? ORDER BY i.id""", pedido))
                .extracting("plato", "cantidad", "precio", "observacion")
                .containsExactly(
                        tuple("Milanesa napolitana", 2, "9500.00", "sin huevo"),
                        tuple("Flan casero", 1, "4000.00", null));
        mvc.perform(get("/api/v1/caja/pedidos").cookie(carla))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem(pedido.intValue())));

        // CP-19-01: Carla aprueba. Se enteran Hugo, A y las cajas, y el KDS lo muestra.
        reloj.avanzar(Duration.ofMinutes(1));
        ayudas.aprobar(carla, pedido).andExpect(status().isNoContent());

        assertThat(evento(cocina)).isEqualTo(new Evento("PEDIDO_APROBADO", pedido, mesa, "APROBADO", null, ahora()));
        assertThat(evento(celular)).isEqualTo(new Evento("APROBADO", pedido, mesa, "APROBADO", null, ahora()));
        assertThat(evento(caja)).isEqualTo(new Evento("PEDIDO_RESUELTO", pedido, mesa, "APROBADO", null, ahora()));
        assertThat(jdbc.queryForObject("SELECT resuelto_por_usuario_id FROM pedido WHERE id = ?", Long.class, pedido))
                .isEqualTo(ayudas.idDe("Carla"));
        Map<String, Object> enElKds = delKds(hugo, "enCola", pedido);
        assertThat(enElKds).containsEntry("mesa", "1");
        // En cualquier orden: ni el contrato ni la consulta lo fijan (sin ORDER BY ni @OrderBy, Abierto de ST-05).
        assertThat(items(enElKds))
                .extracting("plato", "cantidad", "observacion")
                .containsExactlyInAnyOrder(tuple("Milanesa napolitana", 2, "sin huevo"), tuple("Flan casero", 1, null));

        // CP-24-01: Hugo lo empieza y lo termina. A sigue cada paso y Juan se entera de que está listo.
        reloj.avanzar(Duration.ofMinutes(1));
        ayudas.enPreparacion(hugo, pedido).andExpect(status().isNoContent());

        Evento enPreparacion = new Evento("EN_PREPARACION", pedido, mesa, "EN_PREPARACION", null, ahora());
        assertThat(evento(celular)).isEqualTo(enPreparacion);
        // A Cocina también le llega, aunque la tabla de la sección 11 del contrato no lo nombra (Abierto de ST-05).
        assertThat(evento(cocina)).isEqualTo(enPreparacion);

        reloj.avanzar(Duration.ofMinutes(1));
        ayudas.listo(hugo, pedido).andExpect(status().isNoContent());

        Evento listo = new Evento("LISTO", pedido, mesa, "LISTO", null, ahora());
        assertThat(evento(celular)).isEqualTo(listo);
        assertThat(evento(cocina)).isEqualTo(listo);
        assertThat(evento(mozo)).isEqualTo(new Evento("PEDIDO_LISTO", pedido, mesa, "LISTO", null, ahora()));

        // Cada paso guardó su hora, un minuto después del anterior, y la auditoría tiene los tres del personal.
        Map<String, Object> horas = jdbc.queryForMap("""
                SELECT estado, UNIX_TIMESTAMP(enviado_en) AS enviado, UNIX_TIMESTAMP(resuelto_en) AS resuelto,
                       UNIX_TIMESTAMP(en_preparacion_en) AS en_preparacion, UNIX_TIMESTAMP(listo_en) AS listo
                FROM pedido WHERE id = ?""", pedido);
        assertThat(horas.get("estado")).isEqualTo("LISTO");
        assertThat(List.of(horas.get("enviado"), horas.get("resuelto"), horas.get("en_preparacion"), horas.get("listo")))
                .extracting(h -> ((Number) h).longValue())
                .containsExactly(minutosDespues(inicio, 1), minutosDespues(inicio, 2), minutosDespues(inicio, 3),
                        minutosDespues(inicio, 4));
        assertThat(jdbc.queryForList("""
                SELECT r.accion, u.nombre FROM registro_auditoria r JOIN usuario u ON u.id = r.usuario_id
                WHERE r.id > ? ORDER BY r.id""", ayudas.ultimoIdAntes("registro_auditoria")))
                .extracting("accion", "nombre")
                .containsExactly(
                        tuple("APROBAR_PEDIDO", "Carla"),
                        tuple("INICIAR_PREPARACION", "Hugo"),
                        tuple("MARCAR_PEDIDO_LISTO", "Hugo"));

        nadaMas(caja);
        nadaMas(cocina);
        nadaMas(mozo);
        nadaMas(celular);
    }

    @Test
    void siCajaLoRechazaElClienteRecibeElMotivoYCocinaNoSeEntera() throws Exception {
        // CP-19-03, lo que viaja por STOMP.
        Long mesa = ayudas.mesaDe(MESA_1_ESQUINA);
        Cookie carla = ayudas.sesionDe("Carla", "2468");
        Escucha caja = escucharAlPersonal(carla, "caja/pedidos");
        Escucha cocina = escucharAlPersonal(ayudas.sesionDe("Hugo", "1357"), "cocina/cola");
        String a = ayudas.abrirMesa(MESA_1_ESQUINA);
        Escucha celular = escucharAlCliente(a);
        Long pedido = ayudas.pedir(a, ayudas.plato("Milanesa napolitana", "La Esquina"), 1, null);
        assertThat(evento(caja)).isEqualTo(new Evento("PEDIDO_ENVIADO", pedido, mesa, "PENDIENTE_APROBACION", null, ahora()));

        reloj.avanzar(Duration.ofMinutes(1));
        ayudas.rechazar(carla, pedido, """
                {"motivo": "MESA_NO_OCUPADA"}""").andExpect(status().isNoContent());

        assertThat(evento(celular)).isEqualTo(new Evento("RECHAZADO", pedido, mesa, "RECHAZADO", "MESA_NO_OCUPADA", ahora()));
        assertThat(evento(caja)).isEqualTo(new Evento("PEDIDO_RESUELTO", pedido, mesa, "RECHAZADO", null, ahora()));
        nadaMas(cocina);
        nadaMas(caja);
        nadaMas(celular);
    }

    @Test
    void cajaNoSePuedeSuscribirAUnTopicDeElPuerto() throws Exception {
        // El servidor nunca confía en el localId del topic (sección 11 del contrato).
        Escucha carla = conectar(handshakeDelPersonal(ayudas.sesionDe("Carla", "2468")), new StompHeaders());
        String deElPuerto = "/topic/local/" + ayudas.localDe("El Puerto") + "/caja/pedidos";

        carla.suscribir(deElPuerto, texto -> plantilla.convertAndSend(deElPuerto, texto));

        assertThat(carla.errores.poll(TOPE.toMillis(), TimeUnit.MILLISECONDS)).isEqualTo("Ese topic es de otro local");
    }

    // --- Por HTTP

    @Test
    void unaSesionDeLecturaNoPuedePedir() throws Exception {
        // CP-01-03: B escanea la mesa de A y manda un pedido directo, sin pasar por la pantalla.
        ayudas.abrirMesa(MESA_1_ESQUINA);
        String respuesta = ayudas.escanear(MESA_1_ESQUINA)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.esIniciador").value(false))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String b = JsonPath.read(respuesta, "$.tokenSesion");

        ayudas.enviar(b, """
                        {"uuidCliente": "%s", "items": [{"platoId": %d, "cantidad": 1, "observacion": null}]}"""
                        .formatted(UUID.randomUUID(), ayudas.plato("Milanesa napolitana", "La Esquina")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Solo quien abrió la cuenta puede hacer pedidos"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pedido WHERE id > ?", Integer.class,
                ayudas.ultimoIdAntes("pedido"))).isZero();
    }

    @Test
    void conElFiltroSinTaccSoloQuedanLaEnsaladaYElFlan() throws Exception {
        // CP-02-02: la carta de la seed, con el filtro como lo manda la app. Sin filtro también está la Milanesa.
        String menu = "/api/v1/public/mesas/" + MESA_1_ESQUINA + "/menu";
        String completa = mvc.perform(get(menu))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String sinTacc = mvc.perform(get(menu).param("etiqueta", "sin-tacc"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(JsonPath.<List<String>>read(completa, "$.categorias[*].platos[*].nombre"))
                .contains("Milanesa napolitana");
        assertThat(JsonPath.<List<String>>read(sinTacc, "$.categorias[*].platos[*].nombre"))
                .containsExactlyInAnyOrder("Ensalada mixta", "Flan casero");
        assertThat(JsonPath.<List<String>>read(sinTacc, "$.destacados[*].nombre")).containsExactly("Flan casero");
    }

    @Test
    void elPrecioQueMandaElCelularSeIgnora() throws Exception {
        // CP-03-08: la Milanesa con "precio": 1 en el JSON.
        String a = ayudas.abrirMesa(MESA_1_ESQUINA);
        String respuesta = ayudas.enviar(a, """
                        {"uuidCliente": "%s", "items": [{"platoId": %d, "cantidad": 1, "observacion": null, "precio": 1}]}"""
                        .formatted(UUID.randomUUID(), ayudas.plato("Milanesa napolitana", "La Esquina")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Long pedido = ((Number) JsonPath.read(respuesta, "$.id")).longValue();

        assertThat(jdbc.queryForObject("SELECT CAST(precio_unitario AS CHAR) FROM item_pedido WHERE pedido_id = ?",
                String.class, pedido)).isEqualTo("9500.00");
    }

    @Test
    void unPedidoAprobadoNoPasaDirectoAListo() throws Exception {
        // CP-24-03: el cambio directo a Listo, sin pasar por En preparación.
        String a = ayudas.abrirMesa(MESA_1_ESQUINA);
        Long pedido = ayudas.pedir(a, ayudas.plato("Milanesa napolitana", "La Esquina"), 1, null);
        ayudas.aprobar(ayudas.sesionDe("Carla", "2468"), pedido).andExpect(status().isNoContent());

        ayudas.listo(ayudas.sesionDe("Hugo", "1357"), pedido)
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Solo se puede marcar listo un pedido en preparación"));

        Map<String, Object> fila = jdbc.queryForMap("SELECT estado, listo_en, version FROM pedido WHERE id = ?", pedido);
        assertThat(fila).containsEntry("estado", "APROBADO").containsEntry("listo_en", null).containsEntry("version", 1);
        assertThat(ayudas.registrosNuevos()).isEqualTo(1);
    }

    // --- Ayudas: las conexiones

    /** El personal: la cookie del login va en el handshake, como la manda el navegador. Ya escuchando su topic. */
    private Escucha escucharAlPersonal(Cookie sesion, String topic) throws Exception {
        Escucha escucha = conectar(handshakeDelPersonal(sesion), new StompHeaders());
        String destino = "/topic/local/" + ayudas.localDe("La Esquina") + "/" + topic;
        escucha.suscribir(destino, texto -> plantilla.convertAndSend(destino, texto));
        esperarQueEscuche(escucha);
        return escucha;
    }

    /** El cliente anónimo: sin cookie, con su tokenSesion en el CONNECT. Ya escuchando /user/queue/pedido. */
    private Escucha escucharAlCliente(String tokenSesion) throws Exception {
        StompHeaders connect = new StompHeaders();
        connect.add("tokenSesion", tokenSesion);
        Escucha escucha = conectar(new WebSocketHttpHeaders(), connect);
        escucha.suscribir("/user/queue/pedido",
                texto -> plantilla.convertAndSendToUser(tokenSesion, "/queue/pedido", texto));
        esperarQueEscuche(escucha);
        return escucha;
    }

    private static WebSocketHttpHeaders handshakeDelPersonal(Cookie sesion) {
        WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
        handshake.add(HttpHeaders.COOKIE, COOKIE_DE_SESION + "=" + sesion.getValue());
        return handshake;
    }

    private Escucha conectar(WebSocketHttpHeaders handshake, StompHeaders connect) throws Exception {
        Escucha escucha = new Escucha();
        escucha.sesion = stomp.connectAsync("ws://localhost:" + puerto + "/ws", handshake, connect, escucha)
                .get(TOPE.toMillis(), TimeUnit.MILLISECONDS);
        abiertas.add(escucha.sesion);
        return escucha;
    }

    /** Le manda sondas hasta que le llegue una, con tope: recién ahí el broker tiene registrada la suscripción. */
    private static void esperarQueEscuche(Escucha escucha) throws InterruptedException {
        long tope = System.nanoTime() + TOPE.toNanos();
        while (System.nanoTime() < tope) {
            escucha.sonda.accept(SONDA + UUID.randomUUID());
            String llego = escucha.mensajes.poll(200, TimeUnit.MILLISECONDS);
            if (llego != null) {
                assertThat(llego).as("Antes de empezar solo llegan sondas").startsWith(SONDA);
                return;
            }
        }
        fail("La suscripción no quedó activa en " + TOPE.toSeconds() + " segundos");
    }

    // --- Ayudas: los eventos

    /** El próximo evento, con tope. Las sondas no cuentan. */
    private static Evento evento(Escucha escucha) throws InterruptedException {
        long tope = System.nanoTime() + TOPE.toNanos();
        while (true) {
            String frame = escucha.mensajes.poll(tope - System.nanoTime(), TimeUnit.NANOSECONDS);
            if (frame == null) {
                fail("No llegó ningún evento en " + TOPE.toSeconds() + " segundos");
            }
            if (!frame.startsWith(SONDA)) {
                Map<String, Object> json = JsonPath.read(frame, "$");
                assertThat(json.keySet()).as("Los campos de " + frame).isSubsetOf(CAMPOS_DEL_EVENTO);
                return new Evento((String) json.get("tipo"), comoLong(json.get("pedidoId")),
                        comoLong(json.get("mesaId")), (String) json.get("estado"), (String) json.get("detalle"),
                        (String) json.get("ocurridoEn"));
            }
        }
    }

    /**
     * Que no le llegó nada más. Esperar un rato sería un sleep: se le manda una sonda testigo y, cuando llega, no
     * tiene que haber llegado ningún evento. Límite: el broker no garantiza el orden de entrega (no está
     * setPreservePublishOrder), así que un evento de más podría llegar después del testigo y no se vería.
     */
    private static void nadaMas(Escucha escucha) throws InterruptedException {
        String testigo = SONDA + UUID.randomUUID();
        escucha.sonda.accept(testigo);
        List<String> deMas = new ArrayList<>();
        long tope = System.nanoTime() + TOPE.toNanos();
        while (true) {
            String frame = escucha.mensajes.poll(tope - System.nanoTime(), TimeUnit.NANOSECONDS);
            if (frame == null) {
                fail("No llegó la sonda testigo en " + TOPE.toSeconds() + " segundos");
            }
            if (frame.equals(testigo)) {
                break;
            }
            if (!frame.startsWith(SONDA)) {
                deMas.add(frame);
            }
        }
        escucha.mensajes.stream().filter(f -> !f.startsWith(SONDA)).forEach(deMas::add);
        assertThat(deMas).as("Eventos de más").isEmpty();
    }

    private String ahora() {
        return reloj.ahora().toString();
    }

    private static long minutosDespues(Instant inicio, int minutos) {
        return inicio.plus(Duration.ofMinutes(minutos)).getEpochSecond();
    }

    private static Long comoLong(Object numero) {
        return numero == null ? null : ((Number) numero).longValue();
    }

    // --- Ayudas: el KDS

    /** El pedido en una columna del KDS de Hugo (enCola, enPreparacion o listos). */
    private Map<String, Object> delKds(Cookie sesion, String columna, Long pedidoId) throws Exception {
        String respuesta = mvc.perform(get("/api/v1/cocina/pedidos").cookie(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Map<String, Object>> pedidos = JsonPath.read(respuesta, "$." + columna);
        return pedidos.stream()
                .filter(p -> ((Number) p.get("id")).longValue() == pedidoId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("El pedido " + pedidoId + " no está en " + columna));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> pedido) {
        return (List<Map<String, Object>>) pedido.get("items");
    }
}
