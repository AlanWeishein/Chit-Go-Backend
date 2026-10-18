package com.chitgo.usuarios.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.chitgo.usuarios.dominio.Usuario;
import com.chitgo.usuarios.dominio.puertos.EmisorDeHabilitaciones;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeDispositivo;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeSesion;

/**
 * Login con PIN y bloqueo, contra el MySQL del Compose y los usuarios de R__datos_de_prueba.sql.
 *
 * <p>Sin {@code @Transactional} a propósito: lo que hay que probar es que el intento fallido queda guardado
 * aunque la respuesta sea un error, y eso solo se ve si la transacción del Service se confirma de verdad. Por
 * eso cada test deja los usuarios como estaban.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class IngresoConPinTest {

    private static final String STAFF = "/api/v1/public/staff/";
    private static final String PIN = "/api/v1/auth/pin";
    private static final String TOKEN_LA_ESQUINA = "83f39b25-deac-4fde-80cc-4cfdf8117713";
    private static final String TOKEN_EL_PUERTO = "de076a51-1fbd-46f5-98c5-c895ce59a007";
    private static final String BLOQUEADO = "Tu usuario está bloqueado. Pedile al Admin que te blanquee el PIN.";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EmisorDeHabilitaciones habilitaciones;

    @AfterEach
    void dejarLosUsuariosComoEnLaSeed() {
        jdbc.update("UPDATE usuario SET intentos_fallidos = 0, bloqueado_hasta = NULL, activo = TRUE");
    }

    // --- GET /public/staff/{tokenStaff}

    @Test
    void laListaTraeSoloLosQueEntranConPin() throws Exception {
        // CP-10-08: sin el Admin ni el Gerente/Dueño, ordenados por nombre y solo con id y nombre.
        mvc.perform(get(STAFF + TOKEN_LA_ESQUINA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreLocal").value("La Esquina"))
                .andExpect(jsonPath("$.usuarios[*].nombre").value(contains("Ana", "Carla", "Hugo", "Juan")))
                .andExpect(jsonPath("$.usuarios[0].id").value(idDe("Ana", "La Esquina")))
                .andExpect(jsonPath("$.usuarios[0].*", hasSize(2)));
    }

    @Test
    void laListaNoTraeALosDadosDeBaja() throws Exception {
        jdbc.update("UPDATE usuario SET activo = FALSE WHERE id = ?", idDe("Ana", "La Esquina"));

        mvc.perform(get(STAFF + TOKEN_LA_ESQUINA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarios[*].nombre").value(contains("Carla", "Hugo", "Juan")));
    }

    @Test
    void conElTokenDeElPuertoSoloSalenLosDeElPuerto() throws Exception {
        // CP-10-09
        mvc.perform(get(STAFF + TOKEN_EL_PUERTO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreLocal").value("El Puerto"))
                .andExpect(jsonPath("$.usuarios[*].id").value(contains(
                        idDe("Juan", "El Puerto").intValue())));
    }

    @Test
    void unTokenDeStaffQueNoExisteDaUnErrorGenerico() throws Exception {
        mvc.perform(get(STAFF + "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("El QR de staff no es válido."))
                .andExpect(content().string(not(containsString("Esquina"))))
                .andExpect(content().string(not(containsString("Puerto"))));
    }

    // --- POST /auth/pin

    @Test
    void juanEntraConSuPin() throws Exception {
        // CP-10-01
        Long juan = idDe("Juan", "La Esquina");

        Cookie sesion = ingresar(TOKEN_LA_ESQUINA, juan, "1234")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(juan))
                .andExpect(jsonPath("$.nombre").value("Juan"))
                .andExpect(jsonPath("$.rol").value("MOZO"))
                .andExpect(jsonPath("$.local.nombre").value("La Esquina"))
                .andExpect(jsonPath("$.debeCambiarCredencial").value(false))
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);

        assertThat(sesion).isNotNull();
        assertThat(sesion.isHttpOnly()).isTrue();
        assertThat(sesion.getMaxAge()).isEqualTo(10 * 60);
        // Con esa cookie ya tiene sesión de Mozo.
        mvc.perform(get("/api/v1/auth/sesion").cookie(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(juan))
                .andExpect(jsonPath("$.rol").value("MOZO"));
    }

    @Test
    void cajaYCocinaTambienEntranConPin() throws Exception {
        // CP-10-02
        ingresar(TOKEN_LA_ESQUINA, idDe("Carla", "La Esquina"), "2468").andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("CAJA"));
        ingresar(TOKEN_LA_ESQUINA, idDe("Hugo", "La Esquina"), "1357").andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("COCINA"));
    }

    @Test
    void conElPinIncorrectoSumaUnIntento() throws Exception {
        // CP-10-03
        Long juan = idDe("Juan", "La Esquina");

        ingresar(TOKEN_LA_ESQUINA, juan, "0000")
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("PIN incorrecto"));

        assertThat(intentos(juan)).isEqualTo(1);
    }

    @Test
    void entrarBienVuelveLosIntentosACero() throws Exception {
        // CP-10-05, de punta a punta
        Long juan = idDe("Juan", "La Esquina");
        jdbc.update("UPDATE usuario SET intentos_fallidos = 2 WHERE id = ?", juan);

        ingresar(TOKEN_LA_ESQUINA, juan, "1234").andExpect(status().isOk());

        assertThat(intentos(juan)).isZero();
    }

    @Test
    void alQuintoIntentoQuedaBloqueadoYElPinCorrectoTampocoEntra() throws Exception {
        // CP-10-04
        Long juan = idDe("Juan", "La Esquina");
        jdbc.update("UPDATE usuario SET intentos_fallidos = 4 WHERE id = ?", juan);

        ingresar(TOKEN_LA_ESQUINA, juan, "0000")
                .andExpect(status().isLocked())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(BLOQUEADO));
        ingresar(TOKEN_LA_ESQUINA, juan, "1234")
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.detail").value(BLOQUEADO));

        assertThat(intentos(juan)).isEqualTo(5);
        // UNIX_TIMESTAMP no depende de la zona horaria de la conexión.
        assertThat(jdbc.queryForObject("SELECT UNIX_TIMESTAMP(bloqueado_hasta) FROM usuario WHERE id = ?",
                Long.class, juan)).isEqualTo(Usuario.BLOQUEO_SIN_VENCIMIENTO.getEpochSecond());
    }

    @Test
    void cincoPinesMalSeguidosLoBloquean() throws Exception {
        Long juan = idDe("Juan", "La Esquina");

        for (int i = 1; i <= 4; i++) {
            ingresar(TOKEN_LA_ESQUINA, juan, "000" + i).andExpect(status().isUnauthorized());
        }
        ingresar(TOKEN_LA_ESQUINA, juan, "0005").andExpect(status().isLocked());
        // Bloqueado, ni se mira el PIN y el contador no sigue subiendo.
        ingresar(TOKEN_LA_ESQUINA, juan, "0006").andExpect(status().isLocked());

        assertThat(intentos(juan)).isEqualTo(5);
    }

    @Test
    void unUsuarioDeOtroLocalNoEntraNiSumaIntentos() throws Exception {
        // Juan de El Puerto con el QR de La Esquina, con su PIN correcto.
        Long juanDelPuerto = idDe("Juan", "El Puerto");

        ingresar(TOKEN_LA_ESQUINA, juanDelPuerto, "4321")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El usuario no está en la lista de este local."));
        ingresar(TOKEN_LA_ESQUINA, juanDelPuerto, "0000").andExpect(status().isNotFound());

        assertThat(intentos(juanDelPuerto)).isZero();
    }

    @Test
    void elAdminYElGerenteDuenoNoEntranConPin() throws Exception {
        Long admin = jdbc.queryForObject("SELECT id FROM usuario WHERE email = 'admin@laesquina.test'", Long.class);
        Long dueno = jdbc.queryForObject("SELECT id FROM usuario WHERE email = 'dueno@laesquina.test'", Long.class);

        ingresar(TOKEN_LA_ESQUINA, admin, "1234").andExpect(status().isNotFound());
        ingresar(TOKEN_LA_ESQUINA, dueno, "1234").andExpect(status().isNotFound());
    }

    @Test
    void unDadoDeBajaNoEntra() throws Exception {
        Long juan = idDe("Juan", "La Esquina");
        jdbc.update("UPDATE usuario SET activo = FALSE WHERE id = ?", juan);

        ingresar(TOKEN_LA_ESQUINA, juan, "1234").andExpect(status().isNotFound());
    }

    @Test
    void conUnTokenDeStaffQueNoExisteNoEntra() throws Exception {
        ingresar("00000000-0000-0000-0000-000000000000", idDe("Juan", "La Esquina"), "1234")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El QR de staff no es válido."));
    }

    @Test
    void unPinQueNoSonDe4A10DigitosDa400SinSumarIntentos() throws Exception {
        Long juan = idDe("Juan", "La Esquina");

        ingresar(TOKEN_LA_ESQUINA, juan, "12a4")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        ingresar(TOKEN_LA_ESQUINA, juan, "123").andExpect(status().isBadRequest());
        ingresar(TOKEN_LA_ESQUINA, juan, "12345678901").andExpect(status().isBadRequest());

        assertThat(intentos(juan)).isZero();
    }

    /**
     * Siempre con la cookie de dispositivo del usuario elegido, como si ya hubiera entrado con contraseña: lo
     * que pasa sin ella se prueba en IngresoDeMozoTest. A Caja y Cocina no les cambia nada.
     */
    private ResultActions ingresar(String tokenStaff, Long usuarioId, String pin) throws Exception {
        Cookie xsrf = mvc.perform(get("/api/v1/ping")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        Cookie dispositivo = new Cookie(CookieDeDispositivo.NOMBRE, habilitaciones.emitir(usuarioId));
        String cuerpo = """
                {"tokenStaff": "%s", "usuarioId": %d, "pin": "%s"}""".formatted(tokenStaff, usuarioId, pin);
        return mvc.perform(post(PIN).cookie(xsrf, dispositivo).header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private Long idDe(String nombre, String local) {
        return jdbc.queryForObject("""
                SELECT u.id FROM usuario u JOIN local l ON l.id = u.local_id
                WHERE u.nombre = ? AND l.nombre = ?""", Long.class, nombre, local);
    }

    private int intentos(Long usuarioId) {
        return jdbc.queryForObject("SELECT intentos_fallidos FROM usuario WHERE id = ?", Integer.class, usuarioId);
    }
}
