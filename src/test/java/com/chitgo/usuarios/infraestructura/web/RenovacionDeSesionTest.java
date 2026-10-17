package com.chitgo.usuarios.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import com.chitgo.compartido.dominio.RelojDePrueba;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeDispositivo;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeSesion;

/**
 * Renovación del token y corte por inactividad (RNF-14, CU-13 1a): cada request con sesión deja una cookie
 * nueva con el T contado desde ahí, y a los T minutos sin actividad lo próximo da 401. Contra el MySQL del
 * Compose y la seed, sin {@code @Transactional}, por lo mismo que IngresoConPinTest. El reloj es de prueba,
 * como en IngresoDeMozoTest: solo avanza, así que cada test compara contra la hora en la que está.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class RenovacionDeSesionTest {

    private static final String SESION = "/api/v1/auth/sesion";
    /** No existe todavía: con sesión da 404, que quiere decir que pasó la seguridad (como en PrimerIngresoTest). */
    private static final String OTRA_RUTA = "/api/v1/mozo/mesas";
    private static final String TOKEN_LA_ESQUINA = "83f39b25-deac-4fde-80cc-4cfdf8117713";
    private static final String CONTRASENA = "Chitgo2026!";
    private static final Duration DIEZ_MINUTOS = Duration.ofMinutes(10);
    private static final Duration UNA_HORA = Duration.ofHours(1);

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

    @AfterEach
    void dejarLaSeedComoEstaba() {
        jdbc.update("UPDATE local SET minutos_inactividad_sesion = 10");
        jdbc.update("""
                UPDATE usuario SET debe_cambiar_credencial = FALSE, intentos_fallidos = 0, bloqueado_hasta = NULL,
                activo = TRUE""");
    }

    // --- Corte por inactividad

    @Test
    void aLosDiezMinutosSinActividadLaProximaAccionDa401() throws Exception {
        // CP-13-02: T = 10 y el reloj pasa los 10 minutos.
        Cookie sesion = sesionDeJuan();
        reloj.avanzar(DIEZ_MINUTOS.plusSeconds(1));

        accion(sesion)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("No hay una sesión iniciada, o venció."))
                .andExpect(cookie().doesNotExist(CookieDeSesion.NOMBRE));
        mvc.perform(get(SESION).cookie(sesion)).andExpect(status().isUnauthorized());
    }

    @Test
    void unSegundoAntesDeLosDiezMinutosSigueAbierta() throws Exception {
        Cookie sesion = sesionDeJuan();
        reloj.avanzar(DIEZ_MINUTOS.minusSeconds(1));

        accion(sesion).andExpect(status().isNotFound());
    }

    @Test
    void cadaAccionReiniciaElContador() throws Exception {
        // CP-13-03: hace algo a los 9 minutos y queda 9 minutos más sin actividad.
        Cookie delLogin = sesionDeJuan();
        reloj.avanzar(Duration.ofMinutes(9));

        MockHttpServletResponse respuesta = accion(delLogin).andExpect(status().isNotFound()).andReturn().getResponse();
        Cookie renovada = respuesta.getCookie(CookieDeSesion.NOMBRE);
        assertThat(renovada).isNotNull();
        assertThat(cookiesDeSesion(respuesta)).singleElement().satisfies(cookie -> assertThat(cookie)
                .contains("Max-Age=600", "Path=/", "HttpOnly", "SameSite=Strict"));
        mvc.perform(get(SESION).cookie(renovada))
                .andExpect(jsonPath("$.expiraEn").value(reloj.ahora().plus(DIEZ_MINUTOS).toString()));

        reloj.avanzar(Duration.ofMinutes(9));
        // A los 18 minutos del login: con la cookie renovada sigue, con la del login ya no.
        accion(renovada).andExpect(status().isNotFound());
        accion(delLogin).andExpect(status().isUnauthorized());
    }

    @Test
    void consultarLaSesionNoCuentaComoActividad() throws Exception {
        // Es lo que usa Caja para el aviso: /auth/sesion no renueva, así que no mantiene la sesión abierta.
        Instant login = reloj.ahora();
        Cookie sesion = sesionDeJuan();
        reloj.avanzar(Duration.ofMinutes(9));

        mvc.perform(get(SESION).cookie(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiraEn").value(login.plus(DIEZ_MINUTOS).toString()))
                .andExpect(cookie().doesNotExist(CookieDeSesion.NOMBRE));
        reloj.avanzar(Duration.ofMinutes(1).plusSeconds(1));
        mvc.perform(get(SESION).cookie(sesion)).andExpect(status().isUnauthorized());
    }

    // --- T por rol

    @Test
    void cajaTieneUnaHoraAunqueElLocalDigaOtraCosa() throws Exception {
        cambiarT(20);
        Instant login = reloj.ahora();
        Cookie sesion = pin(idDe("Carla"), "2468", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiraEn").value(login.plus(UNA_HORA).toString()))
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
        assertThat(sesion.getMaxAge()).isEqualTo((int) UNA_HORA.toSeconds());

        // A los 55 minutos el front lee expiraEn sin renovar, para el aviso de P-08e.
        reloj.avanzar(Duration.ofMinutes(55));
        mvc.perform(get(SESION).cookie(sesion))
                .andExpect(jsonPath("$.expiraEn").value(login.plus(UNA_HORA).toString()));
        // Si toca algo, tiene otra hora entera.
        Cookie renovada = renovar(sesion);
        assertThat(renovada.getMaxAge()).isEqualTo((int) UNA_HORA.toSeconds());

        reloj.avanzar(Duration.ofMinutes(59));
        accion(renovada).andExpect(status().isNotFound());
        accion(sesion).andExpect(status().isUnauthorized());
    }

    @Test
    void mozoCocinaYAdminUsanElTDelLocal() throws Exception {
        cambiarT(20);
        int veinteMinutos = (int) Duration.ofMinutes(20).toSeconds();

        assertThat(sesionDeJuan().getMaxAge()).isEqualTo(veinteMinutos);
        assertThat(pin(idDe("Hugo"), "1357", null).andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE)
                .getMaxAge()).isEqualTo(veinteMinutos);
        assertThat(ingresarConEmail("admin@laesquina.test").getMaxAge()).isEqualTo(veinteMinutos);
    }

    @Test
    void siElAdminCambiaElTValeDesdeLaProximaAccion() throws Exception {
        // CP-13-04: Juan entró con 10, el Admin lo pasa a 20 y Juan queda 15 minutos sin actividad.
        Cookie sesion = sesionDeJuan();
        assertThat(sesion.getMaxAge()).isEqualTo((int) DIEZ_MINUTOS.toSeconds());
        cambiarT(20);

        Cookie renovada = renovar(sesion);
        assertThat(renovada.getMaxAge()).isEqualTo((int) Duration.ofMinutes(20).toSeconds());
        reloj.avanzar(Duration.ofMinutes(15));
        accion(renovada).andExpect(status().isNotFound());
    }

    @Test
    void elGerenteDuenoTieneDiezMinutosAunqueElLocalDigaOtraCosa() throws Exception {
        cambiarT(20);
        Cookie sesion = ingresarConEmail("dueno@laesquina.test");

        assertThat(sesion.getMaxAge()).isEqualTo((int) DIEZ_MINUTOS.toSeconds());
        assertThat(renovar(sesion).getMaxAge()).isEqualTo((int) DIEZ_MINUTOS.toSeconds());
    }

    // --- Lo que se mira en la base al renovar

    @Test
    void unUsuarioDadoDeBajaSeCortaEnLaProximaAccion() throws Exception {
        // Antes seguía entrando hasta que vencía su token.
        Cookie sesion = sesionDeJuan();
        jdbc.update("UPDATE usuario SET activo = FALSE WHERE id = ?", idDe("Juan"));

        accion(sesion)
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist(CookieDeSesion.NOMBRE));
    }

    @Test
    void unBlanqueoConLaSesionAbiertaValeDesdeLaProximaAccion() throws Exception {
        Cookie sesion = sesionDeJuan();
        marcar(idDe("Juan"), true);

        Cookie renovada = accion(sesion)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CREDENCIAL_PROVISORIA"))
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
        // El token nuevo ya dice provisoria, así que /auth/sesion también.
        mvc.perform(get(SESION).cookie(renovada)).andExpect(jsonPath("$.debeCambiarCredencial").value(true));
    }

    @Test
    void siEligioLaCredencialEnOtraPestanaElTokenViejoYaNoDa403() throws Exception {
        // El token dice provisoria, pero en la base ya no lo es.
        Long juan = idDe("Juan");
        marcar(juan, true);
        Cookie sesion = sesionDeJuan();
        marcar(juan, false);

        accion(sesion).andExpect(status().isNotFound());
    }

    // --- Las rutas que no renuevan

    @Test
    void elLogoutBorraLaCookieSinRenovarla() throws Exception {
        Cookie sesion = sesionDeJuan();
        Cookie xsrf = xsrf();

        MockHttpServletResponse respuesta = mvc.perform(post("/api/v1/auth/logout").cookie(sesion, xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isNoContent())
                .andReturn().getResponse();

        assertThat(cookiesDeSesion(respuesta)).singleElement()
                .satisfies(cookie -> assertThat(cookie).startsWith(CookieDeSesion.NOMBRE + "=;").contains("Max-Age=0"));
    }

    @Test
    void lasRutasPublicasNoRenuevan() throws Exception {
        Cookie sesion = sesionDeJuan();

        mvc.perform(get("/api/v1/ping").cookie(sesion))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist(CookieDeSesion.NOMBRE));
        mvc.perform(get("/api/v1/public/staff/" + TOKEN_LA_ESQUINA).cookie(sesion))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist(CookieDeSesion.NOMBRE));
    }

    @Test
    void entrarConOtroUsuarioDejaSoloLaCookieNueva() throws Exception {
        // La tablet de Caja: si el front no cerró la sesión de Carla, el login de Hugo no se la renueva.
        Cookie deCarla = pin(idDe("Carla"), "2468", null).andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);

        MockHttpServletResponse respuesta = pin(idDe("Hugo"), "1357", deCarla)
                .andExpect(status().isOk())
                .andReturn().getResponse();

        assertThat(cookiesDeSesion(respuesta)).hasSize(1);
        mvc.perform(get(SESION).cookie(respuesta.getCookie(CookieDeSesion.NOMBRE)))
                .andExpect(jsonPath("$.nombre").value("Hugo"));
    }

    // --- Ayudas

    /** Juan habilita el dispositivo con la contraseña y entra con el PIN. */
    private Cookie sesionDeJuan() throws Exception {
        Cookie xsrf = xsrf();
        String cuerpo = json.writeValueAsString(Map.of(
                "tokenStaff", TOKEN_LA_ESQUINA, "nombreUsuario", "juan", "password", CONTRASENA));
        Cookie dispositivo = mvc.perform(post("/api/v1/auth/mozo/login").cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(CookieDeDispositivo.NOMBRE);
        return pin(idDe("Juan"), "1234", dispositivo)
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
    }

    /** Lo que haga el usuario con la sesión: un GET a una ruta que pide sesión y no es de /auth. */
    private ResultActions accion(Cookie sesion) throws Exception {
        return mvc.perform(get(OTRA_RUTA).cookie(sesion));
    }

    /** Una acción que pasa, y la cookie que deja. */
    private Cookie renovar(Cookie sesion) throws Exception {
        Cookie renovada = accion(sesion).andExpect(status().isNotFound())
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
        assertThat(renovada).isNotNull();
        return renovada;
    }

    /**
     * @param cookie la de dispositivo del mozo, o la de sesión de otro usuario que quedó en la tablet; NULL para
     *               entrar sin ninguna
     */
    private ResultActions pin(Long usuarioId, String pin, Cookie cookie) throws Exception {
        Cookie xsrf = xsrf();
        String cuerpo = """
                {"tokenStaff": "%s", "usuarioId": %d, "pin": "%s"}""".formatted(TOKEN_LA_ESQUINA, usuarioId, pin);
        Cookie[] cookies = cookie != null ? new Cookie[] {xsrf, cookie} : new Cookie[] {xsrf};
        return mvc.perform(post("/api/v1/auth/pin").cookie(cookies).header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private Cookie ingresarConEmail(String email) throws Exception {
        Cookie xsrf = xsrf();
        String cuerpo = json.writeValueAsString(Map.of("email", email, "password", CONTRASENA));
        return mvc.perform(post("/api/v1/auth/admin/login").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
    }

    private List<String> cookiesDeSesion(MockHttpServletResponse respuesta) {
        return respuesta.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(cookie -> cookie.startsWith(CookieDeSesion.NOMBRE + "="))
                .toList();
    }

    private Cookie xsrf() throws Exception {
        return mvc.perform(get("/api/v1/ping")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    /** Lo que va a hacer CU-30 cuando esté: el Admin cambia el T del local. */
    private void cambiarT(int minutos) {
        jdbc.update("UPDATE local SET minutos_inactividad_sesion = ? WHERE nombre = 'La Esquina'", minutos);
    }

    private void marcar(Long usuarioId, boolean provisoria) {
        jdbc.update("UPDATE usuario SET debe_cambiar_credencial = ? WHERE id = ?", provisoria, usuarioId);
    }

    private Long idDe(String nombre) {
        return jdbc.queryForObject("""
                SELECT u.id FROM usuario u JOIN local l ON l.id = u.local_id
                WHERE u.nombre = ? AND l.nombre = 'La Esquina'""", Long.class, nombre);
    }
}
