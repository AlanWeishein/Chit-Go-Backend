package com.chitgo.usuarios.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import com.chitgo.usuarios.dominio.Usuario;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeSesion;

/**
 * Login con email y contraseña de Admin y Gerente/Dueño, contra el MySQL del Compose y los usuarios de
 * R__datos_de_prueba.sql. Sin {@code @Transactional}, por lo mismo que IngresoConPinTest: cada test deja los
 * usuarios como estaban.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class IngresoConEmailTest {

    private static final String LOGIN = "/api/v1/auth/admin/login";
    private static final String ADMIN = "admin@laesquina.test";
    private static final String DUENO = "dueno@laesquina.test";
    private static final String CONTRASENA = "Chitgo2026!";
    private static final String INCORRECTOS = "Email o contraseña incorrectos";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @AfterEach
    void dejarLosUsuariosComoEnLaSeed() {
        jdbc.update("UPDATE usuario SET intentos_fallidos = 0, bloqueado_hasta = NULL, activo = TRUE");
    }

    @Test
    void elAdminEntraConSuEmailYContrasena() throws Exception {
        // CP-11-01
        Long admin = idDe(ADMIN);

        Cookie sesion = ingresar(ADMIN, CONTRASENA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(admin))
                .andExpect(jsonPath("$.nombre").value("Admin La Esquina"))
                .andExpect(jsonPath("$.rol").value("ADMIN"))
                .andExpect(jsonPath("$.local.nombre").value("La Esquina"))
                .andExpect(jsonPath("$.debeCambiarCredencial").value(false))
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);

        assertThat(sesion).isNotNull();
        assertThat(sesion.isHttpOnly()).isTrue();
        assertThat(sesion.getMaxAge()).isEqualTo(10 * 60);
        mvc.perform(get("/api/v1/auth/sesion").cookie(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(admin))
                .andExpect(jsonPath("$.rol").value("ADMIN"))
                .andExpect(jsonPath("$.local.nombre").value("La Esquina"));
    }

    @Test
    void elGerenteDuenoEntraSinLocal() throws Exception {
        // CP-11-02, del lado del back: sin local en la sesión, con 10 minutos.
        Cookie sesion = ingresar(DUENO, CONTRASENA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("GERENTE_DUENO"))
                .andExpect(jsonPath("$.local").isEmpty())
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);

        assertThat(sesion.getMaxAge()).isEqualTo(10 * 60);
        mvc.perform(get("/api/v1/auth/sesion").cookie(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Dueño La Esquina"))
                .andExpect(jsonPath("$.local").isEmpty());
    }

    @Test
    void elEmailNoDistingueMayusculasNiEspaciosAlBorde() throws Exception {
        ingresar(" Admin@LaEsquina.test ", CONTRASENA).andExpect(status().isOk());
    }

    @Test
    void conLaContrasenaIncorrectaSumaUnIntento() throws Exception {
        // CP-11-03
        ingresar(ADMIN, "otraContrasena")
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(INCORRECTOS))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        assertThat(intentos(ADMIN)).isEqualTo(1);
    }

    @Test
    void unEmailQueNoExisteDaLaMismaRespuesta() throws Exception {
        // CP-11-04: el cuerpo entero es igual al de la contraseña mal, salvo la ruta, que es la misma.
        String contrasenaMal = ingresar(ADMIN, "otraContrasena").andReturn().getResponse().getContentAsString();
        String emailQueNoExiste = ingresar("nadie@laesquina.test", "otraContrasena")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(emailQueNoExiste)).isEqualTo(json.readTree(contrasenaMal));
    }

    @Test
    void entrarBienVuelveLosIntentosACero() throws Exception {
        jdbc.update("UPDATE usuario SET intentos_fallidos = 3 WHERE email = ?", ADMIN);

        ingresar(ADMIN, CONTRASENA).andExpect(status().isOk());

        assertThat(intentos(ADMIN)).isZero();
    }

    @Test
    void alQuintoIntentoQuedaBloqueadoYLaCorrectaTampocoEntra() throws Exception {
        // CP-11-05: acá el bloqueo no da 423, da el mismo 401.
        jdbc.update("UPDATE usuario SET intentos_fallidos = 4 WHERE email = ?", ADMIN);

        ingresar(ADMIN, "otraContrasena")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(INCORRECTOS));
        ingresar(ADMIN, CONTRASENA)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(INCORRECTOS))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        assertThat(intentos(ADMIN)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT UNIX_TIMESTAMP(bloqueado_hasta) FROM usuario WHERE email = ?",
                Long.class, ADMIN)).isEqualTo(Usuario.BLOQUEO_SIN_VENCIMIENTO.getEpochSecond());
    }

    @Test
    void elGerenteDuenoTambienSeBloquea() throws Exception {
        for (int i = 1; i <= 5; i++) {
            ingresar(DUENO, "otraContrasena" + i).andExpect(status().isUnauthorized());
        }
        ingresar(DUENO, CONTRASENA).andExpect(status().isUnauthorized());

        assertThat(intentos(DUENO)).isEqualTo(5);
    }

    @Test
    void unDadoDeBajaNoEntraNiSumaIntentos() throws Exception {
        // CP-11-06
        jdbc.update("UPDATE usuario SET activo = FALSE WHERE email = ?", ADMIN);

        ingresar(ADMIN, CONTRASENA)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(INCORRECTOS));
        ingresar(ADMIN, "otraContrasena").andExpect(status().isUnauthorized());

        assertThat(intentos(ADMIN)).isZero();
    }

    @Test
    void unMozoConEmailNoEntraPorAca() throws Exception {
        // La seed no le pone email a ningún mozo; si alguno lo tuviera, igual no entra por este login.
        Long juan = jdbc.queryForObject("SELECT id FROM usuario WHERE nombre_usuario = 'juan' AND local_id = "
                + "(SELECT id FROM local WHERE nombre = 'La Esquina')", Long.class);
        jdbc.update("UPDATE usuario SET email = 'juan@laesquina.test' WHERE id = ?", juan);
        try {
            ingresar("juan@laesquina.test", CONTRASENA)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value(INCORRECTOS));
            assertThat(intentos("juan@laesquina.test")).isZero();
        } finally {
            jdbc.update("UPDATE usuario SET email = NULL WHERE id = ?", juan);
        }
    }

    @Test
    void unaInyeccionSqlEnElEmailDaElMensajeGenerico() throws Exception {
        // CP-11-07 (RNF-15)
        for (String email : new String[] {"' OR '1'='1' --", ADMIN + "' -- ", "' OR 1=1; DROP TABLE usuario; --"}) {
            ingresar(email, "cualquierCosa")
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value(INCORRECTOS))
                    .andExpect(content().string(not(containsStringIgnoringCase("sql"))))
                    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        }

        // No le sumó un intento a nadie: la consulta no encontró ningún usuario.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE intentos_fallidos > 0", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuario", Integer.class)).isPositive();
    }

    @Test
    void unaContrasenaFueraDeLargoDa400SinSumarIntentos() throws Exception {
        ingresar(ADMIN, "1234567")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        ingresar(ADMIN, "a".repeat(65)).andExpect(status().isBadRequest());
        // 40 caracteres, pero 80 bytes: BCrypt no miraría los últimos 8.
        ingresar(ADMIN, "ñ".repeat(40)).andExpect(status().isBadRequest());

        assertThat(intentos(ADMIN)).isZero();
    }

    @Test
    void sinEmailDa400() throws Exception {
        ingresar("", CONTRASENA).andExpect(status().isBadRequest());
        ingresar("a".repeat(161), CONTRASENA).andExpect(status().isBadRequest());
    }

    private ResultActions ingresar(String email, String password) throws Exception {
        Cookie xsrf = mvc.perform(get("/api/v1/ping")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        String cuerpo = json.writeValueAsString(new Pedido(email, password));
        return mvc.perform(post(LOGIN).cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private Long idDe(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
    }

    private int intentos(String email) {
        return jdbc.queryForObject("SELECT intentos_fallidos FROM usuario WHERE email = ?", Integer.class, email);
    }

    private record Pedido(String email, String password) {
    }
}
