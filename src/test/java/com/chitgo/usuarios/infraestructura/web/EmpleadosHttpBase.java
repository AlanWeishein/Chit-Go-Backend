package com.chitgo.usuarios.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import com.chitgo.compartido.dominio.RelojDePrueba;
import com.chitgo.usuarios.dominio.puertos.EmisorDeHabilitaciones;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeSesion;

/**
 * Lo común de los tests HTTP de HU-14 (tarea 4 de la ST-22): contra el MySQL del Compose y la seed, sin
 * {@code @Transactional}, con el login de verdad. Cada test deja la base como la seed: borra lo que creó (auditoría,
 * pedidos, ausencias, empleados y usuarios con id mayor al de antes) y devuelve tal cual los turnos, las mesas y los
 * usuarios de la seed. El reloj es de prueba, como en BlanqueoYAusenciaEnElLoginTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(EmpleadosHttpBase.ConRelojDePrueba.class)
abstract class EmpleadosHttpBase {

    protected static final String EMPLEADOS = "/api/v1/admin/empleados";
    protected static final String STAFF = "/api/v1/public/staff/";
    protected static final String TOKEN_LA_ESQUINA = "83f39b25-deac-4fde-80cc-4cfdf8117713";
    protected static final String CONTRASENA = "Chitgo2026!";
    protected static final String SIN_PERMISO = "No tenés permiso para hacer esto.";
    protected static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    private static final List<String> TABLAS_DE_LA_FOTO = List.of("usuario", "empleado", "turno", "mozo_mesa", "ausencia");

    @TestConfiguration
    static class ConRelojDePrueba {

        @Bean
        @Primary
        RelojDePrueba relojDePrueba() {
            return new RelojDePrueba(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        }
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected RelojDePrueba reloj;

    @Autowired
    private EmisorDeHabilitaciones habilitaciones;

    private Long ultimaAuditoria;
    private Long ultimoPedido;
    private Long ultimaClienteSesion;
    private Long ultimaCuenta;
    private Long ultimoEmpleado;
    private Long ultimoUsuario;
    private List<Map<String, Object>> turnosDeLaSeed;
    private List<Map<String, Object>> mesasDeLaSeed;
    private List<Map<String, Object>> usuariosDeLaSeed;
    private Cookie adminEnCache;

    @BeforeEach
    void anotarComoEstaLaSeed() {
        ultimaAuditoria = maximo("registro_auditoria");
        ultimoPedido = maximo("pedido");
        ultimaClienteSesion = maximo("cliente_sesion");
        ultimaCuenta = maximo("cuenta");
        ultimoEmpleado = maximo("empleado");
        ultimoUsuario = maximo("usuario");
        turnosDeLaSeed = jdbc.queryForList("SELECT * FROM turno");
        mesasDeLaSeed = jdbc.queryForList("SELECT * FROM mozo_mesa");
        usuariosDeLaSeed = jdbc.queryForList("SELECT * FROM usuario");
        adminEnCache = null;
    }

    @AfterEach
    void dejarLaBaseComoLaSeed() {
        jdbc.update("DELETE FROM registro_auditoria WHERE id > ?", ultimaAuditoria);
        jdbc.update("DELETE FROM pedido WHERE id > ?", ultimoPedido);
        jdbc.update("DELETE FROM cliente_sesion WHERE id > ?", ultimaClienteSesion);
        jdbc.update("DELETE FROM cuenta WHERE id > ?", ultimaCuenta);
        jdbc.update("DELETE FROM ausencia");
        jdbc.update("DELETE FROM mozo_mesa");
        jdbc.update("DELETE FROM turno");
        insertar("turno", turnosDeLaSeed);
        insertar("mozo_mesa", mesasDeLaSeed);
        jdbc.update("DELETE FROM empleado WHERE id > ?", ultimoEmpleado);
        jdbc.update("DELETE FROM usuario WHERE id > ?", ultimoUsuario);
        for (Map<String, Object> fila : usuariosDeLaSeed) {
            jdbc.update("""
                    UPDATE usuario SET nombre = ?, nombre_usuario = ?, pin_hash = ?, hash_password = ?, activo = ?,
                    intentos_fallidos = ?, bloqueado_hasta = ?, debe_cambiar_credencial = ?,
                    contrasena_provisoria = ? WHERE id = ?""",
                    fila.get("nombre"), fila.get("nombre_usuario"), fila.get("pin_hash"), fila.get("hash_password"),
                    fila.get("activo"), fila.get("intentos_fallidos"), fila.get("bloqueado_hasta"),
                    fila.get("debe_cambiar_credencial"), fila.get("contrasena_provisoria"), fila.get("id"));
        }
    }

    // --- Lo que se pide

    protected LocalDate hoy() {
        return reloj.ahora().atZone(ZONA).toLocalDate();
    }

    /** Suma la cookie y el header de CSRF, como cualquier POST, PUT, PATCH o DELETE del front. */
    protected ResultActions enviar(MockHttpServletRequestBuilder pedido) throws Exception {
        Cookie xsrf = xsrf();
        return mvc.perform(pedido.cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()));
    }

    protected Cookie xsrf() throws Exception {
        return mvc.perform(get("/api/v1/ping")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    protected String cuerpo(Object valor) {
        return json.writeValueAsString(valor);
    }

    protected String altaDeMozo(String nombre, String nombreUsuario, String pin) {
        return cuerpo(Map.of("nombre", nombre, "rol", "MOZO", "pin", pin, "nombreUsuario", nombreUsuario,
                "password", CONTRASENA));
    }

    protected Long id(ResultActions respuesta) throws Exception {
        return ((Number) JsonPath.read(respuesta.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    // --- El login de verdad

    /** El Admin de la seed, con su email. Una sola vez por test. */
    protected Cookie sesionDelAdmin() throws Exception {
        if (adminEnCache == null) {
            adminEnCache = sesionConEmail("admin@laesquina.test");
        }
        return adminEnCache;
    }

    protected Cookie sesionConEmail(String email) throws Exception {
        Cookie sesion = enviar(post("/api/v1/auth/admin/login").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("email", email, "password", CONTRASENA))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
        assertThat(sesion).isNotNull();
        return sesion;
    }

    /** Un mozo, Caja o Cocina de La Esquina con su PIN, con la cookie de dispositivo que emite el back. */
    protected Cookie sesionDe(String nombre, String pin) throws Exception {
        Long usuarioId = usuarioDe(nombre);
        Cookie sesion = pin(usuarioId, pin, dispositivoDe(usuarioId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
        assertThat(sesion).isNotNull();
        return sesion;
    }

    protected Cookie dispositivoDe(Long usuarioId) {
        return new Cookie(com.chitgo.usuarios.infraestructura.seguridad.CookieDeDispositivo.NOMBRE,
                habilitaciones.emitir(usuarioId));
    }

    protected ResultActions pin(Long usuarioId, String pin, Cookie dispositivo) throws Exception {
        String pedido = """
                {"tokenStaff": "%s", "usuarioId": %d, "pin": "%s"}""".formatted(TOKEN_LA_ESQUINA, usuarioId, pin);
        return enviar(post("/api/v1/auth/pin").cookie(dispositivo)
                .contentType(MediaType.APPLICATION_JSON).content(pedido));
    }

    // --- La base

    protected Long usuarioDe(String nombre) {
        return jdbc.queryForObject("""
                SELECT u.id FROM usuario u JOIN local l ON l.id = u.local_id
                WHERE u.nombre = ? AND l.nombre = 'La Esquina'""", Long.class, nombre);
    }

    protected Long empleadoDe(String nombre) {
        return jdbc.queryForObject("""
                SELECT e.id FROM empleado e JOIN usuario u ON u.id = e.usuario_id JOIN local l ON l.id = e.local_id
                WHERE u.nombre = ? AND l.nombre = 'La Esquina'""", Long.class, nombre);
    }

    protected Long empleadoDeElPuerto() {
        return jdbc.queryForObject("""
                SELECT e.id FROM empleado e JOIN usuario u ON u.id = e.usuario_id JOIN local l ON l.id = e.local_id
                WHERE u.nombre = 'Juan' AND l.nombre = 'El Puerto'""", Long.class);
    }

    protected Long usuarioDelEmpleado(Long empleadoId) {
        return jdbc.queryForObject("SELECT usuario_id FROM empleado WHERE id = ?", Long.class, empleadoId);
    }

    protected Long mesaDe(String numero, String local) {
        return jdbc.queryForObject("""
                SELECT m.id FROM mesa m JOIN local l ON l.id = m.local_id WHERE m.numero = ? AND l.nombre = ?""",
                Long.class, numero, local);
    }

    protected Long localDe(String nombre) {
        return jdbc.queryForObject("SELECT id FROM local WHERE nombre = ?", Long.class, nombre);
    }

    /** Las filas de las tablas que toca HU-14. Dos fotos iguales: no cambió nada. */
    protected Map<String, List<Map<String, Object>>> foto() {
        Map<String, List<Map<String, Object>>> foto = new LinkedHashMap<>();
        for (String tabla : TABLAS_DE_LA_FOTO) {
            foto.put(tabla, jdbc.queryForList("SELECT * FROM " + tabla + " ORDER BY 1, 2"));
        }
        return foto;
    }

    protected List<Map<String, Object>> auditoriaNueva() {
        return jdbc.queryForList("SELECT * FROM registro_auditoria WHERE id > ? ORDER BY id", ultimaAuditoria);
    }

    protected List<String> accionesNuevas() {
        return auditoriaNueva().stream().map(f -> (String) f.get("accion")).collect(Collectors.toList());
    }

    private Long maximo(String tabla) {
        return jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM " + tabla, Long.class);
    }

    private void insertar(String tabla, List<Map<String, Object>> filas) {
        for (Map<String, Object> fila : filas) {
            List<String> columnas = new ArrayList<>(fila.keySet());
            String marcas = columnas.stream().map(c -> "?").collect(Collectors.joining(", "));
            jdbc.update("INSERT INTO " + tabla + " (" + String.join(", ", columnas) + ") VALUES (" + marcas + ")",
                    columnas.stream().map(fila::get).toArray());
        }
    }
}
