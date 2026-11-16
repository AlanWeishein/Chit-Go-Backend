package com.chitgo.usuarios.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import com.chitgo.compartido.dominio.RelojDePrueba;
import com.chitgo.usuarios.dominio.Usuario;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeDispositivo;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeSesion;

/**
 * Usuario y contraseña del mozo cada 7 días (RF-13, CP-10-11): POST /auth/mozo/login, la cookie de dispositivo
 * en /auth/pin y GET /auth/dispositivo. Contra el MySQL del Compose y la seed, sin {@code @Transactional}, por
 * lo mismo que IngresoConPinTest. El reloj es de prueba para llegar a los 7 días sin esperar: solo avanza, así
 * que cada test compara contra la hora en la que está.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class IngresoDeMozoTest {

    private static final String LOGIN = "/api/v1/auth/mozo/login";
    private static final String PIN = "/api/v1/auth/pin";
    private static final String DISPOSITIVO = "/api/v1/auth/dispositivo";
    private static final String TOKEN_LA_ESQUINA = "83f39b25-deac-4fde-80cc-4cfdf8117713";
    private static final String TOKEN_EL_PUERTO = "de076a51-1fbd-46f5-98c5-c895ce59a007";
    private static final String CONTRASENA = "Chitgo2026!";
    private static final String INCORRECTOS = "Usuario o contraseña incorrectos";
    private static final String BLOQUEADO = "Tu usuario está bloqueado. Pedile al Admin que te blanquee el PIN.";
    private static final Duration SIETE_DIAS = Duration.ofDays(7);

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
    void dejarLosUsuariosComoEnLaSeed() {
        jdbc.update("UPDATE usuario SET intentos_fallidos = 0, bloqueado_hasta = NULL, activo = TRUE");
        jdbc.update("UPDATE usuario SET nombre_usuario = NULL, hash_password = NULL WHERE nombre = 'Carla'");
    }

    // --- POST /auth/mozo/login

    @Test
    void juanHabilitaElDispositivoConSuUsuarioYContrasena() throws Exception {
        Long juan = idDe("Juan", "La Esquina");

        Cookie dispositivo = habilitar(TOKEN_LA_ESQUINA, "juan", CONTRASENA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(juan))
                .andExpect(jsonPath("$.nombre").value("Juan"))
                .andExpect(jsonPath("$.venceEn").value(reloj.ahora().plus(SIETE_DIAS).toString()))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/v1/auth")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
                .andReturn().getResponse().getCookie(CookieDeDispositivo.NOMBRE);

        assertThat(dispositivo).isNotNull();
        assertThat(dispositivo.isHttpOnly()).isTrue();
        assertThat(dispositivo.getMaxAge()).isEqualTo((int) SIETE_DIAS.toSeconds());
        // No abre la sesión: después va al PIN.
        mvc.perform(get("/api/v1/auth/sesion").cookie(dispositivo)).andExpect(status().isUnauthorized());
    }

    @Test
    void conLaCookieDeDispositivoJuanEntraConElPin() throws Exception {
        // CP-10-11, de punta a punta: contraseña, después PIN.
        Long juan = idDe("Juan", "La Esquina");
        Cookie dispositivo = cookieDeDispositivo("juan");

        Cookie sesion = pin(juan, "1234", dispositivo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(juan))
                .andExpect(jsonPath("$.rol").value("MOZO"))
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);

        assertThat(sesion).isNotNull();
    }

    @Test
    void sinLaCookieDeDispositivoElMozoNoLlegaAlPin() throws Exception {
        // CP-10-11: 401 con el código para que el front muestre P-32, con el PIN bien o mal, y sin sumar intentos.
        Long juan = idDe("Juan", "La Esquina");

        for (String unPin : new String[] {"1234", "0000"}) {
            pin(juan, unPin, null)
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.codigo").value("CONTRASENA_REQUERIDA"))
                    .andExpect(jsonPath("$.detail").value("Ingresá tu usuario y contraseña."))
                    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        }

        assertThat(intentos(juan)).isZero();
    }

    @Test
    void laCookieDeDispositivoVenceALosSieteDias() throws Exception {
        // CP-10-11: pasados los 7 días le vuelve a pedir usuario y contraseña.
        Long juan = idDe("Juan", "La Esquina");
        Cookie dispositivo = cookieDeDispositivo("juan");

        reloj.avanzar(SIETE_DIAS.minusSeconds(1));
        pin(juan, "1234", dispositivo).andExpect(status().isOk());
        dispositivo(dispositivo).andExpect(status().isOk());

        reloj.avanzar(Duration.ofSeconds(2));
        pin(juan, "1234", dispositivo)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CONTRASENA_REQUERIDA"));
        dispositivo(dispositivo).andExpect(status().isNoContent());
    }

    @Test
    void laCookieDeOtroMozoNoSirve() throws Exception {
        // Ana entró con contraseña en este celular; Juan no puede usar su cookie.
        Cookie deAna = cookieDeDispositivo("ana");

        pin(idDe("Juan", "La Esquina"), "1234", deAna)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CONTRASENA_REQUERIDA"));
        pin(idDe("Ana", "La Esquina"), "5678", deAna).andExpect(status().isOk());
    }

    @Test
    void unaCookieAlteradaOLaDeSesionNoSirven() throws Exception {
        Long juan = idDe("Juan", "La Esquina");
        Cookie dispositivo = cookieDeDispositivo("juan");
        Cookie sesion = pin(juan, "1234", dispositivo).andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);

        String[] partes = dispositivo.getValue().split("\\.");
        char[] firma = partes[2].toCharArray();
        int medio = firma.length / 2;
        firma[medio] = firma[medio] == 'A' ? 'B' : 'A';
        Cookie alterada = new Cookie(CookieDeDispositivo.NOMBRE, partes[0] + "." + partes[1] + "." + new String(firma));
        Cookie laDeSesion = new Cookie(CookieDeDispositivo.NOMBRE, sesion.getValue());

        for (Cookie trucha : new Cookie[] {alterada, laDeSesion}) {
            pin(juan, "1234", trucha)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("CONTRASENA_REQUERIDA"));
            dispositivo(trucha).andExpect(status().isNoContent());
        }
    }

    @Test
    void cajaYCocinaNoNecesitanLaCookieDeDispositivo() throws Exception {
        pin(idDe("Carla", "La Esquina"), "2468", null).andExpect(status().isOk());
        pin(idDe("Hugo", "La Esquina"), "1357", null).andExpect(status().isOk());
    }

    @Test
    void unMozoBloqueadoSinCookieVeElBloqueoYNoP32() throws Exception {
        // El bloqueo se mira antes que la cookie: si no, nunca se enteraría de que tiene que pedir el blanqueo.
        Long juan = idDe("Juan", "La Esquina");
        jdbc.update("UPDATE usuario SET intentos_fallidos = 5, bloqueado_hasta = ? WHERE id = ?",
                Timestamp.from(Usuario.BLOQUEO_SIN_VENCIMIENTO), juan);

        pin(juan, "1234", null)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.detail").value(BLOQUEADO));
    }

    @Test
    void conLaContrasenaIncorrectaSumaUnIntento() throws Exception {
        Long juan = idDe("Juan", "La Esquina");

        habilitar(TOKEN_LA_ESQUINA, "juan", "otraContrasena")
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(INCORRECTOS))
                .andExpect(jsonPath("$.codigo").doesNotExist())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        assertThat(intentos(juan)).isEqualTo(1);
    }

    @Test
    void unUsuarioQueNoExisteDaLaMismaRespuesta() throws Exception {
        String contrasenaMal = habilitar(TOKEN_LA_ESQUINA, "juan", "otraContrasena")
                .andReturn().getResponse().getContentAsString();
        String usuarioQueNoExiste = habilitar(TOKEN_LA_ESQUINA, "nadie", "otraContrasena")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(usuarioQueNoExiste)).isEqualTo(json.readTree(contrasenaMal));
    }

    @Test
    void entrarBienVuelveLosIntentosACero() throws Exception {
        // Comparte el contador con el PIN: los que dejó el PIN también vuelven a 0.
        Long juan = idDe("Juan", "La Esquina");
        jdbc.update("UPDATE usuario SET intentos_fallidos = 3 WHERE id = ?", juan);

        habilitar(TOKEN_LA_ESQUINA, "juan", CONTRASENA).andExpect(status().isOk());

        assertThat(intentos(juan)).isZero();
    }

    @Test
    void alQuintoIntentoQuedaBloqueadoYLaCorrectaTampocoEntra() throws Exception {
        Long juan = idDe("Juan", "La Esquina");
        jdbc.update("UPDATE usuario SET intentos_fallidos = 4 WHERE id = ?", juan);

        habilitar(TOKEN_LA_ESQUINA, "juan", "otraContrasena")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(INCORRECTOS));
        habilitar(TOKEN_LA_ESQUINA, "juan", CONTRASENA)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(INCORRECTOS))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        assertThat(intentos(juan)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT UNIX_TIMESTAMP(bloqueado_hasta) FROM usuario WHERE id = ?",
                Long.class, juan)).isEqualTo(Usuario.BLOQUEO_SIN_VENCIMIENTO.getEpochSecond());
        // Con el PIN se entera de que está bloqueado.
        pin(juan, "1234", null).andExpect(status().isLocked());
    }

    @Test
    void elUsuarioEsUnicoDentroDelLocalDelQr() throws Exception {
        // Los dos locales tienen un "juan": el QR decide cuál es.
        habilitar(TOKEN_LA_ESQUINA, "juan", CONTRASENA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idDe("Juan", "La Esquina")));
        habilitar(TOKEN_EL_PUERTO, "juan", CONTRASENA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idDe("Juan", "El Puerto")));
    }

    @Test
    void elUsuarioNoDistingueMayusculasNiEspaciosAlBorde() throws Exception {
        habilitar(TOKEN_LA_ESQUINA, " Juan ", CONTRASENA).andExpect(status().isOk());
    }

    @Test
    void unDadoDeBajaNoEntraNiSumaIntentos() throws Exception {
        Long juan = idDe("Juan", "La Esquina");
        jdbc.update("UPDATE usuario SET activo = FALSE WHERE id = ?", juan);

        habilitar(TOKEN_LA_ESQUINA, "juan", CONTRASENA)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(INCORRECTOS));
        habilitar(TOKEN_LA_ESQUINA, "juan", "otraContrasena").andExpect(status().isUnauthorized());

        assertThat(intentos(juan)).isZero();
    }

    @Test
    void alguienQueNoEsMozoNoEntraPorAcaAunqueTengaUsuario() throws Exception {
        // La seed no le pone usuario a Caja; si lo tuviera, igual no entra por este login.
        Long carla = idDe("Carla", "La Esquina");
        String hashDeJuan = jdbc.queryForObject("SELECT hash_password FROM usuario WHERE id = ?", String.class,
                idDe("Juan", "La Esquina"));
        jdbc.update("UPDATE usuario SET nombre_usuario = 'carla', hash_password = ? WHERE id = ?", hashDeJuan, carla);

        habilitar(TOKEN_LA_ESQUINA, "carla", CONTRASENA)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(INCORRECTOS));

        assertThat(intentos(carla)).isZero();
    }

    @Test
    void unaInyeccionSqlEnElUsuarioDaElMensajeGenerico() throws Exception {
        // RNF-15, como CP-11-07 en el login con email.
        for (String usuario : new String[] {"' OR '1'='1' --", "juan' -- ", "' OR 1=1; DROP TABLE usuario; --"}) {
            habilitar(TOKEN_LA_ESQUINA, usuario, "cualquierCosa")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value(INCORRECTOS))
                    .andExpect(content().string(not(containsStringIgnoringCase("sql"))))
                    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        }

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE intentos_fallidos > 0", Integer.class))
                .isZero();
    }

    @Test
    void conUnTokenDeStaffQueNoExisteDa404() throws Exception {
        habilitar("00000000-0000-0000-0000-000000000000", "juan", CONTRASENA)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El QR de staff no es válido."));
    }

    @Test
    void datosFueraDeLargoDan400SinSumarIntentos() throws Exception {
        Long juan = idDe("Juan", "La Esquina");

        habilitar(TOKEN_LA_ESQUINA, "juan", "1234567").andExpect(status().isBadRequest());
        habilitar(TOKEN_LA_ESQUINA, "juan", "a".repeat(65)).andExpect(status().isBadRequest());
        habilitar(TOKEN_LA_ESQUINA, "", CONTRASENA).andExpect(status().isBadRequest());
        habilitar(TOKEN_LA_ESQUINA, "j".repeat(41), CONTRASENA).andExpect(status().isBadRequest());

        assertThat(intentos(juan)).isZero();
    }

    // --- GET /auth/dispositivo

    @Test
    void sinCookieDeDispositivoDa204() throws Exception {
        // Público y sin el header CSRF, porque es un GET.
        mvc.perform(get(DISPOSITIVO))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void conLaCookieDeJuanDiceQuienYHastaCuando() throws Exception {
        Long juan = idDe("Juan", "La Esquina");
        Cookie dispositivo = cookieDeDispositivo("juan");

        dispositivo(dispositivo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(juan))
                .andExpect(jsonPath("$.nombre").value("Juan"))
                .andExpect(jsonPath("$.venceEn").value(reloj.ahora().plus(SIETE_DIAS).toString()))
                // Consultar no renueva los 7 días.
                .andExpect(cookie().doesNotExist(CookieDeDispositivo.NOMBRE));
    }

    @Test
    void siElMozoFueDadoDeBajaDa204() throws Exception {
        Cookie dispositivo = cookieDeDispositivo("juan");
        jdbc.update("UPDATE usuario SET activo = FALSE WHERE id = ?", idDe("Juan", "La Esquina"));

        dispositivo(dispositivo).andExpect(status().isNoContent());
    }

    private Cookie cookieDeDispositivo(String nombreUsuario) throws Exception {
        return habilitar(TOKEN_LA_ESQUINA, nombreUsuario, CONTRASENA)
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(CookieDeDispositivo.NOMBRE);
    }

    private ResultActions habilitar(String tokenStaff, String nombreUsuario, String password) throws Exception {
        Cookie xsrf = xsrf();
        String cuerpo = json.writeValueAsString(new PedidoDeMozo(tokenStaff, nombreUsuario, password));
        return mvc.perform(post(LOGIN).cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    /** @param dispositivo NULL para entrar sin la cookie de dispositivo */
    private ResultActions pin(Long usuarioId, String pin, Cookie dispositivo) throws Exception {
        Cookie xsrf = xsrf();
        String cuerpo = """
                {"tokenStaff": "%s", "usuarioId": %d, "pin": "%s"}""".formatted(TOKEN_LA_ESQUINA, usuarioId, pin);
        Cookie[] cookies = dispositivo != null ? new Cookie[] {xsrf, dispositivo} : new Cookie[] {xsrf};
        return mvc.perform(post(PIN).cookie(cookies).header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions dispositivo(Cookie dispositivo) throws Exception {
        return mvc.perform(get(DISPOSITIVO).cookie(dispositivo));
    }

    private Cookie xsrf() throws Exception {
        return mvc.perform(get("/api/v1/ping")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    private Long idDe(String nombre, String local) {
        return jdbc.queryForObject("""
                SELECT u.id FROM usuario u JOIN local l ON l.id = u.local_id
                WHERE u.nombre = ? AND l.nombre = ?""", Long.class, nombre, local);
    }

    private int intentos(Long usuarioId) {
        return jdbc.queryForObject("SELECT intentos_fallidos FROM usuario WHERE id = ?", Integer.class, usuarioId);
    }

    private record PedidoDeMozo(String tokenStaff, String nombreUsuario, String password) {
    }
}
