package com.chitgo.usuarios.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import com.chitgo.compartido.dominio.RelojDePrueba;
import com.chitgo.usuarios.dominio.TokenInvalidoException;
import com.chitgo.usuarios.infraestructura.seguridad.AutenticadorPorToken;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeDispositivo;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeSesion;

/**
 * El logout con el "válido desde" del usuario (CP-13-01, decidido el 20/10, punto 7): los tokens emitidos antes
 * dan 401, en todos los dispositivos del usuario. Contra el MySQL del Compose y la seed, sin
 * {@code @Transactional}, con el login de verdad, como RenovacionDeSesionTest. El reloj es de prueba y arranca en
 * un segundo justo: el iat del token va en segundos, así que cada test avanza el reloj para que el token de antes
 * quede en un segundo anterior al logout.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class CierreDeSesionTest {

    private static final String SESION = "/api/v1/auth/sesion";
    private static final String LOGOUT = "/api/v1/auth/logout";
    /** No existe: con sesión da 404, que quiere decir que pasó la seguridad (como en RenovacionDeSesionTest). */
    private static final String OTRA_RUTA = "/api/v1/ruta-de-prueba-que-no-existe";
    private static final String TOKEN_LA_ESQUINA = "83f39b25-deac-4fde-80cc-4cfdf8117713";
    private static final String CONTRASENA = "Chitgo2026!";
    private static final String SIN_SESION = "No hay una sesión iniciada, o venció.";

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

    @Autowired
    private AutenticadorPorToken autenticador;

    @AfterEach
    void dejarLaSeedComoEstaba() {
        jdbc.update("""
                UPDATE usuario SET sesion_valida_desde = NULL, intentos_fallidos = 0, bloqueado_hasta = NULL,
                activo = TRUE""");
    }

    @Test
    void despuesDelLogoutElTokenDeAntesDa401() throws Exception {
        // CP-13-01: alguien se copió el token antes de que Juan cerrara la sesión.
        Cookie copiado = sesionDeJuan();
        reloj.avanzar(Duration.ofSeconds(1));

        logout(copiado).andExpect(status().isNoContent());

        accion(copiado)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(SIN_SESION))
                .andExpect(cookie().doesNotExist(CookieDeSesion.NOMBRE));
        mvc.perform(get(SESION).cookie(copiado)).andExpect(status().isUnauthorized());
        assertThat(validaDesde(idDe("Juan"))).isEqualTo(reloj.ahora());
    }

    @Test
    void juanVuelveAEntrarEnseguidaYSuTokenNuevoSirve() throws Exception {
        // En el mismo segundo del logout: el reloj no se mueve entre el logout y el login.
        Cookie anterior = sesionDeJuan();
        reloj.avanzar(Duration.ofSeconds(1));
        logout(anterior).andExpect(status().isNoContent());

        Cookie nueva = sesionDeJuan();

        mvc.perform(get(SESION).cookie(nueva))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Juan"));
        // La renovación sigue igual: el token renovado nace después del "válido desde".
        Cookie renovada = accion(nueva).andExpect(status().isNotFound())
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
        assertThat(renovada).isNotNull();
        reloj.avanzar(Duration.ofMinutes(1));
        accion(renovada).andExpect(status().isNotFound());
        accion(anterior).andExpect(status().isUnauthorized());
    }

    @Test
    void elLogoutEnUnCelularCierraTambienElOtro() throws Exception {
        // Decidido el 20/10: un mozo con dos celulares los pierde a los dos.
        Cookie primerCelular = sesionDeJuan();
        Cookie segundoCelular = sesionDeJuan();
        reloj.avanzar(Duration.ofSeconds(1));

        logout(primerCelular).andExpect(status().isNoContent());

        accion(segundoCelular).andExpect(status().isUnauthorized());
        // Desde el segundo celular ya no puede ni cerrar la sesión: el token no vale.
        logout(segundoCelular).andExpect(status().isUnauthorized());
    }

    @Test
    void elLogoutDeJuanNoAfectaElTokenDeCarla() throws Exception {
        Cookie deCarla = pin(idDe("Carla"), "2468", null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
        Cookie deJuan = sesionDeJuan();
        reloj.avanzar(Duration.ofSeconds(1));

        logout(deJuan).andExpect(status().isNoContent());

        accion(deCarla).andExpect(status().isNotFound());
        mvc.perform(get(SESION).cookie(deCarla))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Carla"));
        assertThat(validaDesde(idDe("Carla"))).isNull();
    }

    @Test
    void elTokenDeAntesTampocoAbreElWebSocket() throws Exception {
        // El CONNECT de STOMP autentica con AutenticadorPorToken: es lo que valida el interceptor del WebSocket.
        Cookie copiado = sesionDeJuan();
        reloj.avanzar(Duration.ofSeconds(1));
        logout(copiado).andExpect(status().isNoContent());
        Cookie nueva = sesionDeJuan();

        assertThrows(TokenInvalidoException.class, () -> autenticador.autenticar(copiado.getValue()));
        assertThat(autenticador.autenticar(nueva.getValue()).isAuthenticated()).isTrue();
    }

    @Test
    void unTokenDelMismoSegundoDelLogoutSigueEntrando() throws Exception {
        // Límite escrito en la ST-23, tarea 3: el iat va en segundos, y para que el login de enseguida entre, un
        // token emitido en el mismo segundo del logout también pasa. Si este test empieza a fallar, se cerró la
        // ventana (por ejemplo con milisegundos en el token y en la columna).
        Cookie delMismoSegundo = sesionDeJuan();

        logout(delMismoSegundo).andExpect(status().isNoContent());

        accion(delMismoSegundo).andExpect(status().isNotFound());
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

    /** Lo que haga el usuario con la sesión: un GET a una ruta que pide sesión y renueva. */
    private ResultActions accion(Cookie sesion) throws Exception {
        return mvc.perform(get(OTRA_RUTA).cookie(sesion));
    }

    private ResultActions logout(Cookie sesion) throws Exception {
        Cookie xsrf = xsrf();
        return mvc.perform(post(LOGOUT).cookie(sesion, xsrf).header("X-XSRF-TOKEN", xsrf.getValue()));
    }

    /** @param dispositivo la cookie de dispositivo del mozo; NULL para Caja y Cocina */
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

    /** Con UNIX_TIMESTAMP, como IngresoDeMozoTest con bloqueado_hasta, así no depende de la zona de la conexión. */
    private Instant validaDesde(Long usuarioId) {
        Long segundos = jdbc.queryForObject("SELECT UNIX_TIMESTAMP(sesion_valida_desde) FROM usuario WHERE id = ?",
                Long.class, usuarioId);
        return segundos == null ? null : Instant.ofEpochSecond(segundos);
    }

    private Long idDe(String nombre) {
        return jdbc.queryForObject("""
                SELECT u.id FROM usuario u JOIN local l ON l.id = u.local_id
                WHERE u.nombre = ? AND l.nombre = 'La Esquina'""", Long.class, nombre);
    }
}
