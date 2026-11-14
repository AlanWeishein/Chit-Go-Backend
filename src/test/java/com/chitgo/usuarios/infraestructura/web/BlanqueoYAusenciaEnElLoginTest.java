package com.chitgo.usuarios.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import com.chitgo.compartido.dominio.RelojDePrueba;
import com.chitgo.usuarios.dominio.Usuario;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeDispositivo;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeSesion;

/**
 * HU-14, tarea 3 de la ST-22, entrando con el login de verdad: el blanqueo del PIN (CP-10-06), la ausencia vigente
 * en el login (CP-10-13 y CP-27-05) y la contraseña provisoria (decidido el 20/10, puntos 10 y 11). Contra el MySQL
 * del Compose y la seed, sin {@code @Transactional}, por lo mismo que IngresoConPinTest. El reloj es de prueba para
 * pasar al día siguiente de la fecha de vuelta: "hoy" es el día en Buenos Aires de ese reloj. Cada test deja la base
 * como la seed: borra sus ausencias y su auditoría, y les devuelve a los usuarios los hashes y las marcas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class BlanqueoYAusenciaEnElLoginTest {

    private static final String STAFF = "/api/v1/public/staff/";
    private static final String EMPLEADOS = "/api/v1/admin/empleados/";
    private static final String CREDENCIAL = "/api/v1/auth/credencial";
    private static final String SESION = "/api/v1/auth/sesion";
    private static final String OTRA_RUTA = "/api/v1/ruta-de-prueba-que-no-existe";
    private static final String TOKEN_LA_ESQUINA = "83f39b25-deac-4fde-80cc-4cfdf8117713";
    private static final String CONTRASENA = "Chitgo2026!";
    private static final String CONTRASENA_NUEVA = "MiClaveNueva1";
    private static final String BLOQUEADO = "Tu usuario está bloqueado. Pedile al Admin que te blanquee el PIN.";
    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    @TestConfiguration
    static class ConRelojDePrueba {

        @Bean
        @Primary
        RelojDePrueba relojDePrueba() {
            return new RelojDePrueba(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private RelojDePrueba reloj;

    private List<Map<String, Object>> hashesDeLaSeed;
    private Long ultimaAuditoriaAntes;

    @BeforeEach
    void guardarComoEstaLaSeed() {
        hashesDeLaSeed = jdbc.queryForList("SELECT id, pin_hash, hash_password FROM usuario");
        ultimaAuditoriaAntes = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM registro_auditoria", Long.class);
    }

    @AfterEach
    void dejarLaBaseComoLaSeed() {
        jdbc.update("DELETE FROM ausencia");
        jdbc.update("DELETE FROM registro_auditoria WHERE id > ?", ultimaAuditoriaAntes);
        for (Map<String, Object> fila : hashesDeLaSeed) {
            jdbc.update("UPDATE usuario SET pin_hash = ?, hash_password = ? WHERE id = ?",
                    fila.get("pin_hash"), fila.get("hash_password"), fila.get("id"));
        }
        jdbc.update("""
                UPDATE usuario SET debe_cambiar_credencial = FALSE, contrasena_provisoria = FALSE,
                intentos_fallidos = 0, bloqueado_hasta = NULL, activo = TRUE""");
    }

    // --- POST /admin/empleados/{id}/pin

    @Test
    void CP_10_06_elAdminBlanqueaAJuanBloqueadoYJuanEntraConElProvisorio() throws Exception {
        Long juan = usuarioDe("Juan");
        Cookie dispositivo = cookieDeDispositivo("juan", CONTRASENA);
        bloquear(juan);
        pin(juan, "1234", dispositivo).andExpect(status().isLocked()).andExpect(jsonPath("$.detail").value(BLOQUEADO));

        blanquear(empleadoDe("Juan"), "{\"pin\": \"0000\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Juan"))
                .andExpect(jsonPath("$.bloqueado").value(false))
                .andExpect(jsonPath("$.debeCambiarCredencial").value(true))
                .andExpect(jsonPath("$.contrasenaProvisoria").value(false));
        assertThat(intentos(juan)).isZero();

        // El PIN de antes ya no sirve, y con el provisorio entra, pero solo puede elegir la credencial.
        pin(juan, "1234", dispositivo).andExpect(status().isUnauthorized());
        Cookie sesion = pin(juan, "0000", dispositivo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debeCambiarCredencial").value(true))
                .andExpect(jsonPath("$.contrasenaProvisoria").value(false))
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
        assertThat(intentos(juan)).isZero();
        mvc.perform(get(OTRA_RUTA).cookie(sesion))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CREDENCIAL_PROVISORIA"));
        mvc.perform(get(SESION).cookie(sesion))
                .andExpect(jsonPath("$.debeCambiarCredencial").value(true))
                .andExpect(jsonPath("$.contrasenaProvisoria").value(false));

        // Después de un blanqueo alcanza con el PIN: la contraseña no se le vuelve a pedir.
        elegir(sesion, "4321", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debeCambiarCredencial").value(false));
        pin(juan, "4321", dispositivo).andExpect(status().isOk());
    }

    @Test
    void elBlanqueoQuedaAuditadoSinElPin() throws Exception {
        Long empleado = empleadoDe("Juan");
        bloquear(usuarioDe("Juan"));

        blanquear(empleado, "{\"pin\": \"0000\"}").andExpect(status().isOk());

        List<Map<String, Object>> filas = jdbc.queryForList("""
                SELECT accion, entidad, entidad_id, CAST(valor_anterior AS CHAR) AS antes,
                       CAST(valor_nuevo AS CHAR) AS despues
                FROM registro_auditoria WHERE id > ?""", ultimaAuditoriaAntes);
        assertThat(filas).hasSize(1);
        Map<String, Object> fila = filas.get(0);
        assertThat(fila.get("accion")).isEqualTo("BLANQUEAR_PIN");
        assertThat(fila.get("entidad")).isEqualTo("EMPLEADO");
        assertThat(((Number) fila.get("entidad_id")).longValue()).isEqualTo(empleado);
        String valores = (fila.get("antes") + " " + fila.get("despues")).toLowerCase();
        assertThat(valores).contains("\"bloqueado\": true", "\"bloqueado\": false")
                .doesNotContain("0000", "1234", "$2", "pinhash", "pin_hash");
    }

    @Test
    void blanquearAUnaMozaNuevaNoLeSacaLaContrasenaProvisoria() throws Exception {
        Long ana = usuarioDe("Ana");
        jdbc.update("UPDATE usuario SET contrasena_provisoria = TRUE WHERE id = ?", ana);

        blanquear(empleadoDe("Ana"), "{\"pin\": \"0000\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contrasenaProvisoria").value(true));

        assertThat(jdbc.queryForObject("SELECT contrasena_provisoria FROM usuario WHERE id = ?", Boolean.class, ana))
                .isTrue();
    }

    @Test
    void blanquearAUnDadoDeBajaDa409YNoCambiaNada() throws Exception {
        Long ana = usuarioDe("Ana");
        jdbc.update("UPDATE usuario SET activo = FALSE WHERE id = ?", ana);
        String pinHash = pinHash(ana);

        blanquear(empleadoDe("Ana"), "{\"pin\": \"0000\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El empleado está dado de baja"));

        assertThat(pinHash(ana)).isEqualTo(pinHash);
        assertThat(auditoriasNuevas()).isZero();
    }

    @Test
    void sinPinOConUnoMalFormadoDa400YNoCambiaNada() throws Exception {
        Long juan = usuarioDe("Juan");
        bloquear(juan);

        blanquear(empleadoDe("Juan"), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Falta el PIN"));
        for (String pin : new String[] {"123", "12345678901", "12a4"}) {
            blanquear(empleadoDe("Juan"), "{\"pin\": \"" + pin + "\"}").andExpect(status().isBadRequest());
        }

        assertThat(intentos(juan)).isEqualTo(Usuario.INTENTOS_PARA_BLOQUEAR);
        assertThat(auditoriasNuevas()).isZero();
    }

    @Test
    void elAdminDeLaEsquinaNoBlanqueaAUnEmpleadoDeElPuerto() throws Exception {
        Long juanDeElPuerto = jdbc.queryForObject("""
                SELECT e.id FROM empleado e JOIN usuario u ON u.id = e.usuario_id JOIN local l ON l.id = e.local_id
                WHERE u.nombre = 'Juan' AND l.nombre = 'El Puerto'""", Long.class);
        Long usuario = jdbc.queryForObject("SELECT usuario_id FROM empleado WHERE id = ?", Long.class, juanDeElPuerto);
        String pinHash = pinHash(usuario);

        blanquear(juanDeElPuerto, "{\"pin\": \"0000\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El empleado no existe"));

        assertThat(pinHash(usuario)).isEqualTo(pinHash);
    }

    // --- La ausencia vigente en el login

    @Test
    void CP_10_13_conVacacionesQueCubrenHoyJuanNoSaleEnLaListaYNoEntra() throws Exception {
        Long juan = usuarioDe("Juan");
        Cookie dispositivo = cookieDeDispositivo("juan", CONTRASENA);
        ausencia("Juan", hoy().minusDays(1), hoy().plusDays(2));

        mvc.perform(get(STAFF + TOKEN_LA_ESQUINA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarios[*].nombre").value(contains("Ana", "Carla", "Hugo")));
        // Con su PIN correcto y su cookie de dispositivo: el mismo 404 que a un dado de baja, sin sumar intentos.
        pin(juan, "1234", dispositivo)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El usuario no está en la lista de este local."));
        habilitar("juan", CONTRASENA)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Usuario o contraseña incorrectos"));
        habilitar("juan", "OtraClave123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Usuario o contraseña incorrectos"));
        assertThat(intentos(juan)).isZero();
    }

    @Test
    void conUnaAusenciaVigenteNiSiquieraElBloqueadoVeEl423() throws Exception {
        Long carla = usuarioDe("Carla");
        bloquear(carla);
        ausencia("Carla", hoy(), null);

        pin(carla, "2468", null).andExpect(status().isNotFound());
        assertThat(intentos(carla)).isEqualTo(Usuario.INTENTOS_PARA_BLOQUEAR);
    }

    @Test
    void CP_27_05_anaVuelveSolaAlDiaSiguienteDeSuFechaDeVuelta() throws Exception {
        Long ana = usuarioDe("Ana");
        Cookie dispositivo = cookieDeDispositivo("ana", CONTRASENA);
        ausencia("Ana", hoy(), hoy().plusDays(2));

        // El primer día y el último no entra.
        enLaLista(false);
        pin(ana, "5678", dispositivo).andExpect(status().isNotFound());
        reloj.avanzar(Duration.ofDays(2));
        enLaLista(false);
        pin(ana, "5678", dispositivo).andExpect(status().isNotFound());

        // Al día siguiente vuelve sola, sin que nadie toque la ausencia.
        reloj.avanzar(Duration.ofDays(1));
        enLaLista(true);
        pin(ana, "5678", dispositivo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ana"));
        assertThat(intentos(ana)).isZero();
    }

    @Test
    void unaAusenciaQueEmpiezaMananaNoLaSacaHoy() throws Exception {
        ausencia("Ana", hoy().plusDays(1), hoy().plusDays(5));

        enLaLista(true);
        pin(usuarioDe("Ana"), "5678", cookieDeDispositivo("ana", CONTRASENA)).andExpect(status().isOk());
    }

    @Test
    void unaAusenciaYaTerminadaNoLaSaca() throws Exception {
        ausencia("Ana", hoy().minusDays(10), hoy().minusDays(1));

        enLaLista(true);
    }

    @Test
    void laAusenciaDeOtroEmpleadoNoSacaANadieMas() throws Exception {
        ausencia("Hugo", hoy(), null);

        mvc.perform(get(STAFF + TOKEN_LA_ESQUINA))
                .andExpect(jsonPath("$.usuarios[*].nombre").value(contains("Ana", "Carla", "Juan")));
        pin(usuarioDe("Carla"), "2468", null).andExpect(status().isOk());
    }

    // --- contrasenaProvisoria en el login y en /auth/credencial

    @Test
    void conLaContrasenaProvisoriaElMozoTieneQueElegirlaYDespuesDejaDeSerlo() throws Exception {
        Long juan = usuarioDe("Juan");
        Cookie dispositivo = cookieDeDispositivo("juan", CONTRASENA);
        // Como lo deja el alta de HU-14.
        jdbc.update("UPDATE usuario SET debe_cambiar_credencial = TRUE, contrasena_provisoria = TRUE WHERE id = ?",
                juan);

        Cookie sesion = pin(juan, "1234", dispositivo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debeCambiarCredencial").value(true))
                .andExpect(jsonPath("$.contrasenaProvisoria").value(true))
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
        mvc.perform(get(SESION).cookie(sesion)).andExpect(jsonPath("$.contrasenaProvisoria").value(true));

        elegir(sesion, "4321", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Elegí tu contraseña."));
        assertThat(provisoria(juan)).isTrue();
        assertThat(pinHash(juan)).isEqualTo(hashDeLaSeed(juan, "pin_hash"));

        Cookie nueva = elegir(sesion, "4321", CONTRASENA_NUEVA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debeCambiarCredencial").value(false))
                .andExpect(jsonPath("$.contrasenaProvisoria").value(false))
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
        assertThat(provisoria(juan)).isFalse();
        mvc.perform(get(SESION).cookie(nueva)).andExpect(jsonPath("$.contrasenaProvisoria").value(false));
        habilitar("juan", CONTRASENA_NUEVA).andExpect(status().isOk());
    }

    @Test
    void cajaYElAdminTienenContrasenaProvisoriaEnFalse() throws Exception {
        pin(usuarioDe("Carla"), "2468", null).andExpect(jsonPath("$.contrasenaProvisoria").value(false));
        Cookie admin = sesionDelAdmin();
        mvc.perform(get(SESION).cookie(admin)).andExpect(jsonPath("$.contrasenaProvisoria").value(false));
    }

    // --- Ayudas

    private LocalDate hoy() {
        return reloj.ahora().atZone(ZONA).toLocalDate();
    }

    private void enLaLista(boolean anaEsta) throws Exception {
        List<String> esperados = anaEsta ? List.of("Ana", "Carla", "Hugo", "Juan") : List.of("Carla", "Hugo", "Juan");
        mvc.perform(get(STAFF + TOKEN_LA_ESQUINA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarios[*].nombre").value(contains(esperados.toArray())));
    }

    /** Vacaciones en La Esquina, cargadas por el Admin de la seed. {@code hasta} NULL: sin fecha de vuelta. */
    private void ausencia(String nombre, LocalDate desde, LocalDate hasta) {
        jdbc.update("""
                INSERT INTO ausencia (local_id, empleado_id, tipo, desde, hasta, creada_por_usuario_id)
                SELECT e.local_id, e.id, 'VACACIONES', ?, ?, (SELECT id FROM usuario WHERE email = 'admin@laesquina.test')
                FROM empleado e JOIN usuario u ON u.id = e.usuario_id JOIN local l ON l.id = e.local_id
                WHERE u.nombre = ? AND l.nombre = 'La Esquina'""", desde, hasta, nombre);
    }

    private ResultActions blanquear(Long empleadoId, String cuerpo) throws Exception {
        Cookie admin = sesionDelAdmin();
        Cookie xsrf = xsrf();
        return mvc.perform(post(EMPLEADOS + empleadoId + "/pin").cookie(admin, xsrf)
                .header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private Cookie sesionDelAdmin() throws Exception {
        Cookie xsrf = xsrf();
        String cuerpo = json.writeValueAsString(Map.of("email", "admin@laesquina.test", "password", CONTRASENA));
        return mvc.perform(post("/api/v1/auth/admin/login").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
    }

    private ResultActions elegir(Cookie sesion, String pin, String password) throws Exception {
        Cookie xsrf = xsrf();
        Map<String, String> cuerpo = new java.util.HashMap<>();
        cuerpo.put("pin", pin);
        cuerpo.put("pinRepetido", pin);
        cuerpo.put("password", password);
        cuerpo.put("passwordRepetida", password);
        return mvc.perform(post(CREDENCIAL).cookie(sesion, xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo)));
    }

    private Cookie cookieDeDispositivo(String nombreUsuario, String password) throws Exception {
        return habilitar(nombreUsuario, password)
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(CookieDeDispositivo.NOMBRE);
    }

    private ResultActions habilitar(String nombreUsuario, String password) throws Exception {
        Cookie xsrf = xsrf();
        String cuerpo = json.writeValueAsString(Map.of(
                "tokenStaff", TOKEN_LA_ESQUINA, "nombreUsuario", nombreUsuario, "password", password));
        return mvc.perform(post("/api/v1/auth/mozo/login").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    /** @param dispositivo NULL para Caja y Cocina */
    private ResultActions pin(Long usuarioId, String pin, Cookie dispositivo) throws Exception {
        Cookie xsrf = xsrf();
        String cuerpo = """
                {"tokenStaff": "%s", "usuarioId": %d, "pin": "%s"}""".formatted(TOKEN_LA_ESQUINA, usuarioId, pin);
        Cookie[] cookies = dispositivo != null ? new Cookie[] {xsrf, dispositivo} : new Cookie[] {xsrf};
        return mvc.perform(post("/api/v1/auth/pin").cookie(cookies).header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private Cookie xsrf() throws Exception {
        return mvc.perform(get("/api/v1/ping")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    private void bloquear(Long usuarioId) {
        jdbc.update("UPDATE usuario SET intentos_fallidos = ?, bloqueado_hasta = '2038-01-01 00:00:00' WHERE id = ?",
                Usuario.INTENTOS_PARA_BLOQUEAR, usuarioId);
    }

    private int intentos(Long usuarioId) {
        return jdbc.queryForObject("SELECT intentos_fallidos FROM usuario WHERE id = ?", Integer.class, usuarioId);
    }

    private boolean provisoria(Long usuarioId) {
        return jdbc.queryForObject("SELECT contrasena_provisoria FROM usuario WHERE id = ?", Boolean.class,
                usuarioId);
    }

    private String pinHash(Long usuarioId) {
        return jdbc.queryForObject("SELECT pin_hash FROM usuario WHERE id = ?", String.class, usuarioId);
    }

    private Object hashDeLaSeed(Long usuarioId, String columna) {
        return hashesDeLaSeed.stream()
                .filter(f -> usuarioId.equals(((Number) f.get("id")).longValue()))
                .findFirst().orElseThrow().get(columna);
    }

    private int auditoriasNuevas() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM registro_auditoria WHERE id > ?", Integer.class,
                ultimaAuditoriaAntes);
    }

    private Long usuarioDe(String nombre) {
        return jdbc.queryForObject("""
                SELECT u.id FROM usuario u JOIN local l ON l.id = u.local_id
                WHERE u.nombre = ? AND l.nombre = 'La Esquina'""", Long.class, nombre);
    }

    private Long empleadoDe(String nombre) {
        return jdbc.queryForObject("""
                SELECT e.id FROM empleado e JOIN usuario u ON u.id = e.usuario_id JOIN local l ON l.id = e.local_id
                WHERE u.nombre = ? AND l.nombre = 'La Esquina'""", Long.class, nombre);
    }
}
