package com.chitgo.pedidos.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

import com.chitgo.compartido.dominio.RelojDePrueba;
import com.chitgo.pedidos.aplicacion.MesasSinMozoService;
import com.chitgo.pedidos.dominio.EventoTiempoReal;
import com.chitgo.pedidos.dominio.TipoEvento;
import com.chitgo.pedidos.infraestructura.programacion.AlertaDeMesaSinMozoProgramada;
import com.chitgo.usuarios.dominio.puertos.EmisorDeHabilitaciones;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeDispositivo;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeSesion;

/**
 * La mesa sin mozo de HU-14 de punta a punta (CP-07-05 y CP-01-09, ST-23 tarea 2), contra el MySQL del Compose y
 * R__datos_de_prueba.sql. El reloj de prueba está en el lunes 16 de noviembre de 2026 a las 12:00 del local. La tarea
 * programada está apagada (src/test/resources/config/application.yml): la pasada se llama a mano.
 *
 * <p>En la seed, Juan atiende las mesas 1, 2 y 3, Ana la 3 y la 4, y las 5 y 6 no tienen mozo. Todos los turnos son
 * de 00:00 a 23:59, así que a las 12:00 no empieza ninguno: cada test le cambia el turno del lunes a quien haga
 * falta. Al terminar se vuelven los turnos del lunes a la seed, se rehabilitan las mesas y se borra lo que se creó.
 *
 * <p>El aviso sale por el PublicadorStomp de verdad y se mira en el SimpMessagingTemplate espiado, como en
 * AlertaDeDemoraTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class MesaSinMozoTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDate LUNES = LocalDate.of(2026, 11, 16);
    private static final Instant LUNES_A_LAS_12 = LocalDateTime.of(LUNES, LocalTime.NOON).atZone(ZONA).toInstant();
    private static final String MESAS_SIN_MOZO = "/api/v1/alertas/mesas-sin-mozo";
    private static final String TOKEN_LA_ESQUINA = "83f39b25-deac-4fde-80cc-4cfdf8117713";
    private static final String QR_MESA_4 = "f0c89fba-24a4-4cb4-acff-dee757071597";
    /** Lo que se borra al terminar, en el orden de las FK. */
    private static final List<String> TABLAS_DE_LA_PRUEBA =
            List.of("registro_auditoria", "cliente_sesion", "aviso", "cuenta", "ausencia");

    /** Un envío por STOMP: a dónde fue y qué llevaba. */
    record Envio(String destino, EventoTiempoReal evento) {
    }

    @TestConfiguration
    static class ConReloj {

        @Bean
        @Primary
        RelojDePrueba relojDePrueba() {
            return new RelojDePrueba(LUNES_A_LAS_12);
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
    private MesasSinMozoService mesasSinMozo;

    @Autowired
    private ScheduledTaskHolder tareas;

    @MockitoSpyBean
    private SimpMessagingTemplate plantilla;

    private final Map<String, Long> ultimoIdAntes = new HashMap<>();
    private Long esquina;
    private Long puerto;
    private Long juan;
    private Long ana;
    private Long juanDelPuerto;

    @BeforeEach
    void anotarLoQueHabia() {
        for (String tabla : TABLAS_DE_LA_PRUEBA) {
            ultimoIdAntes.put(tabla, jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM " + tabla, Long.class));
        }
        esquina = jdbc.queryForObject("SELECT id FROM local WHERE nombre = 'La Esquina'", Long.class);
        puerto = jdbc.queryForObject("SELECT id FROM local WHERE nombre = 'El Puerto'", Long.class);
        juan = empleado("Juan", esquina);
        ana = empleado("Ana", esquina);
        juanDelPuerto = empleado("Juan", puerto);
    }

    @AfterEach
    void dejarLaBaseComoEnLaSeed() {
        for (String tabla : TABLAS_DE_LA_PRUEBA) {
            jdbc.update("DELETE FROM " + tabla + " WHERE id > ?", ultimoIdAntes.get(tabla));
        }
        jdbc.update("UPDATE turno SET hora_inicio = '00:00:00', hora_fin = '23:59:00' "
                + "WHERE dia_semana = 'LUN' AND empleado_id IN (?, ?, ?)", juan, ana, juanDelPuerto);
        jdbc.update("UPDATE mesa SET inhabilitada = FALSE WHERE local_id IN (?, ?)", esquina, puerto);
        reloj.avanzar(Duration.between(reloj.ahora(), LUNES_A_LAS_12));
    }

    // --- CP-07-05: al empezar los turnos

    @Test
    void cp_07_05_a_las_12_empiezan_juan_y_ana_y_avisan_las_mesas_5_y_6() {
        turnoDelLunes(juan, "12:00:00", "16:00:00");
        turnoDelLunes(ana, "12:00:00", "16:00:00");

        mesasSinMozo.avisarAlEmpezarLosTurnos();

        assertThat(alertas()).extracting(Envio::destino).containsOnly(topicDeAlertas(esquina));
        assertThat(alertas()).extracting(e -> e.evento().mesaId())
                .containsExactly(mesa(esquina, "5"), mesa(esquina, "6"));
        assertThat(alertas()).extracting(e -> e.evento().detalle())
                .containsExactly("La mesa 5 no tiene mozo de turno", "La mesa 6 no tiene mozo de turno");
    }

    @Test
    void a_las_12_01_no_se_repite_porque_el_turno_ya_no_esta_empezando() {
        turnoDelLunes(juan, "12:00:00", "16:00:00");
        turnoDelLunes(ana, "12:00:00", "16:00:00");
        reloj.avanzar(Duration.ofMinutes(1));

        mesasSinMozo.avisarAlEmpezarLosTurnos();

        assertThat(alertas()).isEmpty();
    }

    @Test
    void con_ana_ausente_hoy_avisa_la_mesa_4_y_no_la_3_que_la_tiene_juan() {
        turnoDelLunes(juan, "12:00:00", "16:00:00");
        turnoDelLunes(ana, "12:00:00", "16:00:00");
        ausenciaDeAna(LUNES, LUNES);

        mesasSinMozo.avisarAlEmpezarLosTurnos();

        assertThat(alertas()).extracting(e -> e.evento().mesaId())
                .containsExactly(mesa(esquina, "4"), mesa(esquina, "5"), mesa(esquina, "6"));
    }

    @Test
    void una_mesa_inhabilitada_no_avisa() {
        turnoDelLunes(juan, "12:00:00", "16:00:00");
        turnoDelLunes(ana, "12:00:00", "16:00:00");
        jdbc.update("UPDATE mesa SET inhabilitada = TRUE WHERE id = ?", mesa(esquina, "5"));

        mesasSinMozo.avisarAlEmpezarLosTurnos();

        assertThat(alertas()).extracting(e -> e.evento().mesaId()).containsExactly(mesa(esquina, "6"));
    }

    @Test
    void un_turno_que_empieza_en_el_puerto_no_avisa_nada_en_la_esquina() {
        // Si la pasada mirara todos los locales, las mesas 5 y 6 de La Esquina avisarían.
        turnoDelLunes(juanDelPuerto, "12:00:00", "16:00:00");

        mesasSinMozo.avisarAlEmpezarLosTurnos();

        assertThat(alertas()).extracting(Envio::destino).doesNotContain(topicDeAlertas(esquina));
        // Y en El Puerto tampoco: sus dos mesas las tiene Juan, que acaba de entrar.
        assertThat(alertas()).isEmpty();
    }

    // --- El GET de las mesas sin mozo

    @Test
    void el_get_trae_las_mismas_mesas_que_aviso_la_tarea() throws Exception {
        turnoDelLunes(juan, "12:00:00", "16:00:00");
        turnoDelLunes(ana, "12:00:00", "16:00:00");
        ausenciaDeAna(LUNES, LUNES);
        mesasSinMozo.avisarAlEmpezarLosTurnos();
        List<Long> avisadas = alertas().stream().map(e -> e.evento().mesaId()).toList();

        String respuesta = mvc.perform(get(MESAS_SIN_MOZO).cookie(sesionDe("Carla", "2468")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        List<Number> ids = JsonPath.read(respuesta, "$[*].id");
        List<String> numeros = JsonPath.read(respuesta, "$[*].numero");
        assertThat(ids).extracting(Number::longValue).containsExactlyElementsOf(avisadas);
        assertThat(numeros).containsExactly("4", "5", "6");
    }

    @Test
    void el_admin_tambien_puede_pedir_la_lista() throws Exception {
        String respuesta = mvc.perform(get(MESAS_SIN_MOZO).cookie(sesionDelAdmin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // Con los turnos de la seed, solo las que no tienen mozo asignado.
        List<String> numeros = JsonPath.read(respuesta, "$[*].numero");
        assertThat(numeros).containsExactly("5", "6");
    }

    @Test
    void cocina_no_puede_pedir_la_lista() throws Exception {
        mvc.perform(get(MESAS_SIN_MOZO).cookie(sesionDe("Hugo", "1357")))
                .andExpect(status().isForbidden());
    }

    @Test
    void sin_sesion_da_401() throws Exception {
        mvc.perform(get(MESAS_SIN_MOZO)).andExpect(status().isUnauthorized());
    }

    // --- CP-01-09: al abrir la cuenta (el aviso de EntrarALaMesaService, que ya estaba)

    @Test
    void cp_01_09_sin_su_moza_de_turno_la_cuenta_se_abre_sin_mozo_y_avisa_en_alertas() throws Exception {
        // La seed no tiene a Pedro: hace de él Ana, que tiene solo la mesa 4 y hoy entra recién a las 20:00.
        turnoDelLunes(ana, "20:00:00", "23:00:00");
        Long mesa4 = mesa(esquina, "4");

        Cookie xsrf = xsrf();
        mvc.perform(post("/api/v1/public/mesas/" + QR_MESA_4 + "/sesiones").cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isCreated());

        Map<String, Object> cuenta = jdbc.queryForMap("SELECT id, mozo_id FROM cuenta WHERE mesa_id = ? AND id > ?",
                mesa4, ultimoIdAntes.get("cuenta"));
        assertThat(cuenta.get("mozo_id")).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM aviso WHERE tipo = 'MESA_SIN_MOZO' AND id > ?",
                Integer.class, ultimoIdAntes.get("aviso"))).isEqualTo(1);
        assertThat(alertas()).singleElement().satisfies(envio -> {
            assertThat(envio.destino()).isEqualTo(topicDeAlertas(esquina));
            assertThat(envio.evento().mesaId()).isEqualTo(mesa4);
            assertThat(envio.evento().detalle()).isEqualTo("La mesa 4 no tiene mozo de turno");
        });
    }

    @Test
    void en_la_suite_la_tarea_no_queda_agendada() {
        // La otra mitad de AlertaDeMesaSinMozoProgramadaTest: con la propiedad apagada, nada corre en medio de la suite.
        String tarea = AlertaDeMesaSinMozoProgramada.class.getName() + ".revisar";
        assertThat(tareas.getScheduledTasks())
                .map(ScheduledTask::getTask)
                .noneMatch(t -> t.toString().equals(tarea));
    }

    // --- Ayudas: la base

    private Long empleado(String nombre, Long localId) {
        return jdbc.queryForObject("""
                SELECT e.id FROM empleado e JOIN usuario u ON u.id = e.usuario_id
                WHERE u.nombre = ? AND e.local_id = ?""", Long.class, nombre, localId);
    }

    private Long mesa(Long localId, String numero) {
        return jdbc.queryForObject("SELECT id FROM mesa WHERE local_id = ? AND numero = ?", Long.class,
                localId, numero);
    }

    /** Le cambia el turno del lunes; el AfterEach lo vuelve a 00:00 a 23:59. */
    private void turnoDelLunes(Long empleadoId, String inicio, String fin) {
        int cambiados = jdbc.update(
                "UPDATE turno SET hora_inicio = ?, hora_fin = ? WHERE empleado_id = ? AND dia_semana = 'LUN'",
                inicio, fin, empleadoId);
        assertThat(cambiados).isEqualTo(1);
    }

    private void ausenciaDeAna(LocalDate desde, LocalDate hasta) {
        jdbc.update("""
                INSERT INTO ausencia (local_id, empleado_id, tipo, desde, hasta, creada_por_usuario_id)
                VALUES (?, ?, 'FRANCO', ?, ?, (SELECT id FROM usuario WHERE email = 'admin@laesquina.test'))""",
                esquina, ana, desde, hasta);
    }

    // --- Ayudas: lo que salió por STOMP

    /** Los MESA_SIN_MOZO que salieron por el SimpMessagingTemplate, a cualquier destino y en orden. */
    private List<Envio> alertas() {
        return Mockito.mockingDetails(plantilla).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("convertAndSend") && i.getArguments().length == 2)
                .filter(i -> i.getArgument(1) instanceof EventoTiempoReal e && e.tipo() == TipoEvento.MESA_SIN_MOZO)
                .map(i -> new Envio(i.getArgument(0), i.getArgument(1)))
                .toList();
    }

    private static String topicDeAlertas(Long localId) {
        return "/topic/local/" + localId + "/alertas";
    }

    // --- Ayudas: las sesiones

    /** Entra con el PIN como el front, con la cookie de dispositivo del usuario, como en AlertaDeDemoraTest. */
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

    /** Entra con el email y la contraseña, como CartaDelAdminTest. */
    private Cookie sesionDelAdmin() throws Exception {
        Cookie xsrf = xsrf();
        Cookie sesion = mvc.perform(post("/api/v1/auth/admin/login").cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "admin@laesquina.test", "password": "Chitgo2026!"}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
        assertThat(sesion).isNotNull();
        return sesion;
    }

    private Cookie xsrf() throws Exception {
        return mvc.perform(get("/api/v1/ping")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }
}
