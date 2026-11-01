package com.chitgo.pedidos.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.chitgo.compartido.dominio.RelojDePrueba;
import com.chitgo.pedidos.aplicacion.AlertaDeDemoraService;
import com.chitgo.pedidos.dominio.EventoTiempoReal;
import com.chitgo.pedidos.dominio.TipoEvento;
import com.chitgo.pedidos.infraestructura.programacion.AlertaDeDemoraProgramada;
import com.chitgo.usuarios.dominio.puertos.EmisorDeHabilitaciones;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeDispositivo;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeSesion;

/**
 * HU-08 de punta a punta (CP-20-01 a CP-20-04): los pedidos se arman por el camino del cliente, contra el MySQL del
 * Compose y R__datos_de_prueba.sql, y cada test borra lo que dejó, como AprobacionDePedidosTest. La tarea programada
 * está apagada (src/test/resources/config/application.yml): las pasadas se llaman a mano con el reloj de prueba.
 *
 * <p>El aviso sale por el PublicadorStomp de verdad y se mira en el SimpMessagingTemplate espiado. Así queda probado
 * que, con la transacción de solo lectura de revisarDemorados(), el envío del afterCommit sale (ST-10, tarea 1).
 *
 * <p>El service guarda la hora de la pasada anterior y el contexto se comparte entre tests. Por eso cada test hace
 * una pasada después de armar sus pedidos y antes de mover el reloj: desde ahí cuenta su ventana.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AlertaDeDemoraTest {

    private static final String COLA = "/api/v1/caja/pedidos";
    private static final String TOKEN_LA_ESQUINA = "83f39b25-deac-4fde-80cc-4cfdf8117713";
    private static final String MESA_1_ESQUINA = "d4964b6b-fdae-4b4f-83e9-1c1e78a21723";
    private static final String MESA_1_PUERTO = "ab0124a8-6033-4d73-a1f6-3513140e62f7";
    /** Lo que se borra al terminar, en el orden de las FK. */
    private static final List<String> TABLAS_DE_LA_PRUEBA =
            List.of("registro_auditoria", "item_pedido", "pedido", "cliente_sesion", "aviso", "cuenta");

    /** Un envío por STOMP: a dónde fue y qué llevaba. */
    record Envio(String destino, EventoTiempoReal evento) {
    }

    @TestConfiguration
    static class ConReloj {

        @Bean
        @Primary
        RelojDePrueba relojDePrueba() {
            // Sin fracciones de segundo, como guarda enviado_en.
            return new RelojDePrueba(Instant.now().truncatedTo(ChronoUnit.SECONDS));
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
    private AlertaDeDemoraService alertaDeDemora;

    @Autowired
    private ScheduledTaskHolder tareas;

    @MockitoSpyBean
    private SimpMessagingTemplate plantilla;

    private final Map<String, Long> ultimoIdAntes = new HashMap<>();

    @BeforeEach
    void anotarLoQueHabia() {
        for (String tabla : TABLAS_DE_LA_PRUEBA) {
            ultimoIdAntes.put(tabla, jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM " + tabla, Long.class));
        }
    }

    @AfterEach
    void dejarLaBaseComoEnLaSeed() {
        for (String tabla : TABLAS_DE_LA_PRUEBA) {
            jdbc.update("DELETE FROM " + tabla + " WHERE id > ?", ultimoIdAntes.get(tabla));
        }
    }

    // --- El borde de los 3 minutos

    @Test
    void aLosTresMinutosYUnSegundoSaleLaAlertaYElGetLoTraeDemorado() throws Exception {
        // CP-20-01: enviado a las 20:00, la pasada de las 20:03:00 no avisa y la de las 20:03:01 sí.
        String cliente = abrirMesa(MESA_1_ESQUINA);
        Long pedido = pedir(cliente, plato("Milanesa napolitana", "La Esquina"));
        pasada();

        reloj.avanzar(Duration.ofMinutes(3));
        pasada();
        assertThat(alertas()).isEmpty();

        reloj.avanzar(Duration.ofSeconds(1));
        pasada();
        assertThat(alertas()).singleElement().satisfies(envio -> {
            assertThat(envio.destino()).isEqualTo(topicDeCaja("La Esquina"));
            assertThat(envio.evento().pedidoId()).isEqualTo(pedido);
            assertThat(envio.evento().mesaId()).isEqualTo(mesa(MESA_1_ESQUINA));
            assertThat(envio.evento().estado()).isEqualTo("PENDIENTE_APROBACION");
            assertThat(envio.evento().ocurridoEn()).isEqualTo(reloj.ahora().toString());
        });
        assertThat(colaDeEstaPrueba(sesionDe("Carla", "2468"))).singleElement().satisfies(p -> assertThat(p)
                .containsEntry("segundosDeEspera", 181)
                .containsEntry("demorado", true));
    }

    @Test
    void aLosDosMinutosCincuentaYNueveNiAvisoNiDemorado() throws Exception {
        // CP-20-02
        String cliente = abrirMesa(MESA_1_ESQUINA);
        pedir(cliente, plato("Milanesa napolitana", "La Esquina"));
        pasada();

        reloj.avanzar(Duration.ofMinutes(2).plusSeconds(59));
        pasada();

        assertThat(alertas()).isEmpty();
        assertThat(colaDeEstaPrueba(sesionDe("Carla", "2468"))).singleElement().satisfies(p -> assertThat(p)
                .containsEntry("segundosDeEspera", 179)
                .containsEntry("demorado", false));
    }

    // --- Cuando Carla lo resuelve

    @Test
    void siCarlaLoApruebaAntesNoSaleAviso() throws Exception {
        // CP-20-03, como lo plantea ST-10: la aprobación llega antes de la pasada de los 3:01.
        String cliente = abrirMesa(MESA_1_ESQUINA);
        Long pedido = pedir(cliente, plato("Milanesa napolitana", "La Esquina"));
        pasada();
        Cookie carla = sesionDe("Carla", "2468");

        reloj.avanzar(Duration.ofMinutes(2));
        aprobar(carla, pedido).andExpect(status().isNoContent());
        reloj.avanzar(Duration.ofMinutes(1).plusSeconds(1));
        pasada();

        assertThat(alertas()).isEmpty();
        mvc.perform(get(COLA).cookie(carla))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(not(hasItem(pedido.intValue()))));
    }

    @Test
    void siCarlaApruebaUnoConAlertaSaleDeLaColaYNoVuelveAAvisar() throws Exception {
        // CP-20-03, como lo plantea chit-go-casos-de-prueba: el pedido ya tiene la alerta y Carla lo aprueba.
        String cliente = abrirMesa(MESA_1_ESQUINA);
        Long pedido = pedir(cliente, plato("Milanesa napolitana", "La Esquina"));
        pasada();
        reloj.avanzar(Duration.ofMinutes(3).plusSeconds(1));
        pasada();
        assertThat(alertas()).hasSize(1);
        Cookie carla = sesionDe("Carla", "2468");

        aprobar(carla, pedido).andExpect(status().isNoContent());
        reloj.avanzar(Duration.ofSeconds(5));
        pasada();

        assertThat(alertas()).hasSize(1);
        mvc.perform(get(COLA).cookie(carla))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(not(hasItem(pedido.intValue()))));
    }

    @Test
    void sinNadieEnCajaElPedidoDeHaceCincoMinutosSaleDemoradoAlEntrar() throws Exception {
        // CP-20-04, la parte del back: el aviso sale a un topic sin nadie y Carla entra recién a los 5 minutos.
        String cliente = abrirMesa(MESA_1_ESQUINA);
        pedir(cliente, plato("Milanesa napolitana", "La Esquina"));
        pasada();
        for (int i = 0; i < 60; i++) {
            reloj.avanzar(Duration.ofSeconds(5));
            pasada();
        }
        assertThat(alertas()).hasSize(1);

        Cookie carla = sesionDe("Carla", "2468");

        assertThat(colaDeEstaPrueba(carla)).singleElement().satisfies(p -> assertThat(p)
                .containsEntry("segundosDeEspera", 300)
                .containsEntry("demorado", true));
    }

    // --- Una sola vez, y a su local

    @Test
    void tresPasadasSeguidasDespuesDeLosTresMinutosDanUnSoloAviso() throws Exception {
        String cliente = abrirMesa(MESA_1_ESQUINA);
        Long pedido = pedir(cliente, plato("Milanesa napolitana", "La Esquina"));
        pasada();

        reloj.avanzar(Duration.ofMinutes(3).plusSeconds(1));
        pasada();
        reloj.avanzar(Duration.ofSeconds(5));
        pasada();
        reloj.avanzar(Duration.ofSeconds(5));
        pasada();

        assertThat(alertas()).singleElement().satisfies(envio -> assertThat(envio.evento().pedidoId()).isEqualTo(pedido));
    }

    @Test
    void elAvisoDeElPuertoVaAlTopicDeElPuerto() throws Exception {
        // RNF-10: nunca un aviso de un local en el topic de otro.
        String cliente = abrirMesa(MESA_1_PUERTO);
        Long delPuerto = pedir(cliente, plato("Pizza muzzarella", "El Puerto"));
        pasada();

        reloj.avanzar(Duration.ofMinutes(3).plusSeconds(1));
        pasada();

        assertThat(alertas()).singleElement().satisfies(envio -> {
            assertThat(envio.destino()).isEqualTo(topicDeCaja("El Puerto"));
            assertThat(envio.evento().pedidoId()).isEqualTo(delPuerto);
            assertThat(envio.evento().mesaId()).isEqualTo(mesa(MESA_1_PUERTO));
        });
        assertThat(alertas()).extracting(Envio::destino).doesNotContain(topicDeCaja("La Esquina"));
    }

    // --- La tarea programada

    @Test
    void enLaSuiteLaTareaNoQuedaAgendada() {
        // La otra mitad de AlertaDeDemoraProgramadaTest: con la propiedad apagada, nada corre en medio de los tests.
        String tarea = AlertaDeDemoraProgramada.class.getName() + ".revisar";
        assertThat(tareas.getScheduledTasks())
                .map(ScheduledTask::getTask)
                .noneMatch(t -> t.toString().equals(tarea));
    }

    // --- Ayudas: las pasadas y lo que salió por STOMP

    private void pasada() {
        alertaDeDemora.revisarDemorados();
    }

    /** Los ALERTA_DEMORA que salieron por el SimpMessagingTemplate, a cualquier destino y en orden. */
    private List<Envio> alertas() {
        return Mockito.mockingDetails(plantilla).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("convertAndSend") && i.getArguments().length == 2)
                .filter(i -> i.getArgument(1) instanceof EventoTiempoReal e && e.tipo() == TipoEvento.ALERTA_DEMORA)
                .map(i -> new Envio(i.getArgument(0), i.getArgument(1)))
                .toList();
    }

    private String topicDeCaja(String local) {
        Long localId = jdbc.queryForObject("SELECT id FROM local WHERE nombre = ?", Long.class, local);
        return "/topic/local/" + localId + "/caja/pedidos";
    }

    // --- Ayudas: el cliente

    /** El celular escanea el QR de una mesa libre y abre la cuenta: queda como iniciador. */
    private String abrirMesa(String tokenQr) throws Exception {
        Cookie xsrf = xsrf();
        String respuesta = mvc.perform(post("/api/v1/public/mesas/" + tokenQr + "/sesiones").cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(respuesta, "$.tokenSesion");
    }

    /** Manda un pedido de un plato con la sesión del iniciador. Sale con la hora del reloj de prueba. */
    private Long pedir(String tokenSesion, Long platoId) throws Exception {
        Cookie xsrf = xsrf();
        String cuerpo = """
                {"uuidCliente": "%s", "items": [{"platoId": %d, "cantidad": 1, "observacion": null}]}"""
                .formatted(UUID.randomUUID(), platoId);
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

    /** La cola de Carla, solo con los pedidos que armó este test. */
    private List<Map<String, Object>> colaDeEstaPrueba(Cookie sesion) throws Exception {
        String respuesta = mvc.perform(get(COLA).cookie(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Map<String, Object>> cola = JsonPath.read(respuesta, "$");
        return cola.stream().filter(p -> ((Number) p.get("id")).longValue() > ultimoIdAntes.get("pedido")).toList();
    }

    /** Entra con el PIN como el front, con la cookie de dispositivo del usuario, como en ControlDePermisosTest. */
    private Cookie sesionDe(String nombre, String pin) throws Exception {
        Long usuarioId = jdbc.queryForObject("""
                SELECT u.id FROM usuario u JOIN local l ON l.id = u.local_id
                WHERE u.nombre = ? AND l.nombre = 'La Esquina'""", Long.class, nombre);
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

    private Long plato(String nombre, String local) {
        return jdbc.queryForObject("""
                SELECT p.id FROM plato p JOIN local l ON l.id = p.local_id
                WHERE p.nombre = ? AND l.nombre = ?""", Long.class, nombre, local);
    }

    private Long mesa(String tokenQr) {
        return jdbc.queryForObject("SELECT id FROM mesa WHERE token_qr = ?", Long.class, tokenQr);
    }
}
