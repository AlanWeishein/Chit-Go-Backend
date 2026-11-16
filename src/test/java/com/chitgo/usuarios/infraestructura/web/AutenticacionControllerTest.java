package com.chitgo.usuarios.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.usuarios.dominio.DatosDeSesion;
import com.chitgo.usuarios.dominio.puertos.EmisorDeTokens;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeSesion;

/**
 * Seguridad de punta a punta con MockMvc, contra el MySQL del Compose (tiene que estar levantado) y los
 * usuarios de R__datos_de_prueba.sql. El token se emite directo con el EmisorDeTokens: el login es la tarea 3.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class AutenticacionControllerTest {

    private static final String SESION = "/api/v1/auth/sesion";
    private static final String LOGOUT = "/api/v1/auth/logout";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EmisorDeTokens emisor;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void elPingEsPublico() throws Exception {
        mvc.perform(get("/api/v1/ping")).andExpect(status().isOk());
    }

    @Test
    void sinCookieLaSesionDa401EnProblemDetail() throws Exception {
        mvc.perform(get(SESION))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("No hay una sesión iniciada, o venció."));
    }

    @Test
    void cualquierRutaNoPublicaSinCookieDa401() throws Exception {
        mvc.perform(get("/api/v1/caja/pedidos"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void conTokenValidoDevuelveLaSesionSinRenovarLa() throws Exception {
        Long juan = idDeUsuario("juan", "La Esquina");
        Long laEsquina = idDeLocal("La Esquina");
        Cookie cookie = cookieDeSesion(new DatosDeSesion(juan, "MOZO", laEsquina, false), Duration.ofMinutes(10));

        MvcResult resultado = mvc.perform(get(SESION).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(juan))
                .andExpect(jsonPath("$.nombre").value("Juan"))
                .andExpect(jsonPath("$.rol").value("MOZO"))
                .andExpect(jsonPath("$.local.id").value(laEsquina))
                .andExpect(jsonPath("$.local.nombre").value("La Esquina"))
                .andExpect(jsonPath("$.debeCambiarCredencial").value(false))
                .andReturn();

        // expiraEn sale en ISO-8601, a 10 minutos de ahora.
        String expiraEn = com.jayway.jsonpath.JsonPath.read(resultado.getResponse().getContentAsString(), "$.expiraEn");
        assertThat(java.time.Instant.parse(expiraEn))
                .isBetween(java.time.Instant.now().plusSeconds(540), java.time.Instant.now().plusSeconds(601));
        assertThat(resultado.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .noneMatch(c -> c.startsWith(CookieDeSesion.NOMBRE + "="));
    }

    @Test
    void elGerenteDuenoNoTieneLocal() throws Exception {
        Long dueno = jdbc.queryForObject("SELECT id FROM usuario WHERE email = 'dueno@laesquina.test'", Long.class);
        Cookie cookie = cookieDeSesion(new DatosDeSesion(dueno, "GERENTE_DUENO", null, false), Duration.ofMinutes(10));

        mvc.perform(get(SESION).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("GERENTE_DUENO"))
                .andExpect(jsonPath("$.local").value(nullValue()));
    }

    @Test
    void conTokenVencidoOAlteradoDa401() throws Exception {
        Long juan = idDeUsuario("juan", "La Esquina");
        Cookie vencida = cookieDeSesion(new DatosDeSesion(juan, "MOZO", idDeLocal("La Esquina"), false),
                Duration.ofSeconds(-1));

        mvc.perform(get(SESION).cookie(vencida)).andExpect(status().isUnauthorized());
        mvc.perform(get(SESION).cookie(new Cookie(CookieDeSesion.NOMBRE, "no.es.un-token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void conUnUsuarioDadoDeBajaLaSesionDa401() throws Exception {
        Long juan = idDeUsuario("juan", "La Esquina");
        Cookie cookie = cookieDeSesion(new DatosDeSesion(juan, "MOZO", idDeLocal("La Esquina"), false),
                Duration.ofMinutes(10));
        jdbc.update("UPDATE usuario SET activo = FALSE WHERE id = ?", juan);

        mvc.perform(get(SESION).cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void elPrimerGetDelFrontDejaLaCookieCsrfLegibleDesdeJavaScript() throws Exception {
        // El front llama a /auth/sesion al cargar: sin sesión da 401, pero igual le deja el token CSRF.
        MvcResult resultado = mvc.perform(get(SESION)).andExpect(status().isUnauthorized()).andReturn();

        Cookie xsrf = resultado.getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();
        assertThat(xsrf.getValue()).isNotBlank();
        assertThat(xsrf.isHttpOnly()).isFalse();
        // La escribe como Cookie de Servlet: el SameSite queda como atributo, no en el texto del header del mock.
        assertThat(xsrf.getAttribute("SameSite")).isEqualTo("Strict");
    }

    @Test
    void elLogoutDejaLaCookieVencida() throws Exception {
        Cookie sesion = cookieDeSesion(
                new DatosDeSesion(idDeUsuario("juan", "La Esquina"), "MOZO", idDeLocal("La Esquina"), false),
                Duration.ofMinutes(10));
        Cookie xsrf = cookieCsrf();

        MvcResult resultado = mvc.perform(post(LOGOUT).cookie(sesion, xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isNoContent())
                .andReturn();

        Cookie borrada = resultado.getResponse().getCookie(CookieDeSesion.NOMBRE);
        assertThat(borrada).isNotNull();
        assertThat(borrada.getValue()).isEmpty();
        assertThat(borrada.getMaxAge()).isZero();
        assertThat(borrada.isHttpOnly()).isTrue();
    }

    @Test
    void sinElHeaderCsrfUnPostDa403() throws Exception {
        Cookie sesion = cookieDeSesion(
                new DatosDeSesion(idDeUsuario("juan", "La Esquina"), "MOZO", idDeLocal("La Esquina"), false),
                Duration.ofMinutes(10));
        Cookie xsrf = cookieCsrf();

        // Con la cookie pero sin el header: es lo que puede hacer otra página.
        mvc.perform(post(LOGOUT).cookie(sesion, xsrf))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(post(LOGOUT).cookie(sesion, xsrf).header("X-XSRF-TOKEN", "otro-valor"))
                .andExpect(status().isForbidden());
    }

    @Test
    void losLoginsSonPublicosPeroTambienPidenElTokenCsrf() throws Exception {
        mvc.perform(post("/api/v1/auth/pin").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    private Cookie cookieDeSesion(DatosDeSesion datos, Duration duracion) {
        return new Cookie(CookieDeSesion.NOMBRE, emisor.emitir(datos, duracion));
    }

    /** Lo que hace el front: un GET cualquiera le deja la cookie, y la copia en el header. */
    private Cookie cookieCsrf() throws Exception {
        return mvc.perform(get("/api/v1/ping")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    private Long idDeUsuario(String nombreUsuario, String local) {
        return jdbc.queryForObject("""
                SELECT u.id FROM usuario u JOIN local l ON l.id = u.local_id
                WHERE u.nombre_usuario = ? AND l.nombre = ?""", Long.class, nombreUsuario, local);
    }

    private Long idDeLocal(String nombre) {
        return jdbc.queryForObject("SELECT id FROM local WHERE nombre = ?", Long.class, nombre);
    }
}
