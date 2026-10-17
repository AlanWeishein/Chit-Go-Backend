package com.chitgo.usuarios.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import com.chitgo.usuarios.infraestructura.seguridad.CookieDeDispositivo;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeSesion;

/**
 * Primer ingreso (CU-12): con la credencial provisoria solo se puede elegir la propia, y POST /auth/credencial
 * la guarda. Contra el MySQL del Compose y la seed, sin {@code @Transactional}, por lo mismo que
 * IngresoConPinTest. La seed no tiene a nadie con la marca: cada test se la pone a quien usa, y después se le
 * devuelven la marca y los hashes que tenía.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PrimerIngresoTest {

    private static final String CREDENCIAL = "/api/v1/auth/credencial";
    private static final String SESION = "/api/v1/auth/sesion";
    /** No existe todavía: con sesión da 404, y con la credencial provisoria, 403 antes de llegar a buscarla. */
    private static final String OTRA_RUTA = "/api/v1/mozo/mesas";
    private static final String TOKEN_LA_ESQUINA = "83f39b25-deac-4fde-80cc-4cfdf8117713";
    private static final String CONTRASENA = "Chitgo2026!";
    private static final String CONTRASENA_NUEVA = "MiClaveNueva1";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    private List<Map<String, Object>> hashesDeLaSeed;

    @BeforeEach
    void guardarLosHashesDeLaSeed() {
        hashesDeLaSeed = jdbc.queryForList("SELECT id, pin_hash, hash_password FROM usuario");
    }

    @AfterEach
    void dejarLosUsuariosComoEnLaSeed() {
        for (Map<String, Object> fila : hashesDeLaSeed) {
            jdbc.update("UPDATE usuario SET pin_hash = ?, hash_password = ? WHERE id = ?",
                    fila.get("pin_hash"), fila.get("hash_password"), fila.get("id"));
        }
        jdbc.update("""
                UPDATE usuario SET debe_cambiar_credencial = FALSE, intentos_fallidos = 0, bloqueado_hasta = NULL,
                activo = TRUE""");
    }

    // --- Lo que puede hacer con la credencial provisoria

    @Test
    void conLaCredencialProvisoriaOtraRutaDa403() throws Exception {
        // CP-12-07: Juan entró con el PIN provisorio y todavía no lo cambió.
        Cookie sesion = sesionDeMozoConProvisoria();

        mvc.perform(get(OTRA_RUTA).cookie(sesion))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("CREDENCIAL_PROVISORIA"))
                .andExpect(jsonPath("$.detail").value("Elegí tu PIN o tu contraseña antes de seguir."));
    }

    @Test
    void conLaCredencialProvisoriaPuedeVerLaSesionYCerrarla() throws Exception {
        Cookie sesion = sesionDeMozoConProvisoria();

        mvc.perform(get(SESION).cookie(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debeCambiarCredencial").value(true));
        Cookie xsrf = xsrf();
        mvc.perform(post("/api/v1/auth/logout").cookie(sesion, xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isNoContent());
        // Las públicas siguen respondiendo.
        mvc.perform(get("/api/v1/ping").cookie(sesion)).andExpect(status().isOk());
    }

    @Test
    void sinElHeaderCsrfDaElOtro403() throws Exception {
        // El del CSRF corre antes y no lleva el código del primer ingreso.
        Cookie sesion = sesionDeMozoConProvisoria();

        mvc.perform(post(CREDENCIAL).cookie(sesion).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("4321", "4321", null, null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").doesNotExist());
    }

    @Test
    void sinSesionDa401() throws Exception {
        Cookie xsrf = xsrf();
        mvc.perform(post(CREDENCIAL).cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("4321", "4321", null, null)))
                .andExpect(status().isUnauthorized());
    }

    // --- Elegir la credencial

    @Test
    void elMozoEligePinYContrasenaYDespuesEntra() throws Exception {
        // CP-12-01, con la contraseña de P-32b: se guardan, la marca se apaga y la otra ruta ya no da 403.
        Long juan = idDe("Juan");
        Cookie sesion = sesionDeMozoConProvisoria();

        Cookie nueva = elegir(sesion, "4321", "4321", CONTRASENA_NUEVA, CONTRASENA_NUEVA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(juan))
                .andExpect(jsonPath("$.rol").value("MOZO"))
                .andExpect(jsonPath("$.local.nombre").value("La Esquina"))
                .andExpect(jsonPath("$.debeCambiarCredencial").value(false))
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);

        assertThat(nueva).isNotNull();
        assertThat(nueva.isHttpOnly()).isTrue();
        assertThat(debeCambiar(juan)).isFalse();
        mvc.perform(get(OTRA_RUTA).cookie(nueva)).andExpect(status().isNotFound());
        mvc.perform(get(SESION).cookie(nueva)).andExpect(jsonPath("$.debeCambiarCredencial").value(false));

        // Con la contraseña nueva habilita el dispositivo, y con el PIN nuevo entra. Los provisorios ya no sirven.
        habilitar("juan", CONTRASENA).andExpect(status().isUnauthorized());
        Cookie dispositivo = cookieDeDispositivo("juan", CONTRASENA_NUEVA);
        pin(juan, "1234", dispositivo).andExpect(status().isUnauthorized());
        pin(juan, "4321", dispositivo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debeCambiarCredencial").value(false));
    }

    @Test
    void elMozoPuedeElegirSoloElPin() throws Exception {
        // Después de un blanqueo (CP-12-08): pasa otra vez por la pantalla del PIN, y la contraseña queda.
        Long juan = idDe("Juan");
        Cookie sesion = sesionDeMozoConProvisoria();

        elegir(sesion, "4321", "4321", null, null).andExpect(status().isOk());

        assertThat(debeCambiar(juan)).isFalse();
        cookieDeDispositivo("juan", CONTRASENA);
    }

    @Test
    void cajaEligeElPin() throws Exception {
        Long carla = idDe("Carla");
        marcar(carla);
        Cookie sesion = pin(carla, "2468", null)
                .andExpect(jsonPath("$.debeCambiarCredencial").value(true))
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);

        elegir(sesion, "8642", "8642", null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("CAJA"));

        pin(carla, "8642", null).andExpect(status().isOk());
    }

    @Test
    void elAdminEligeLaContrasena() throws Exception {
        Long admin = idDeEmail("admin@laesquina.test");
        marcar(admin);
        Cookie sesion = ingresarConEmail("admin@laesquina.test", CONTRASENA)
                .andExpect(jsonPath("$.debeCambiarCredencial").value(true))
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
        mvc.perform(get(OTRA_RUTA).cookie(sesion)).andExpect(status().isForbidden());

        elegir(sesion, null, null, CONTRASENA_NUEVA, CONTRASENA_NUEVA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("ADMIN"))
                .andExpect(jsonPath("$.debeCambiarCredencial").value(false));

        ingresarConEmail("admin@laesquina.test", CONTRASENA).andExpect(status().isUnauthorized());
        ingresarConEmail("admin@laesquina.test", CONTRASENA_NUEVA).andExpect(status().isOk());
    }

    @Test
    void elGerenteDuenoEligeLaContrasenaYSigueSinLocal() throws Exception {
        marcar(idDeEmail("dueno@laesquina.test"));
        Cookie sesion = ingresarConEmail("dueno@laesquina.test", CONTRASENA)
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);

        Cookie nueva = elegir(sesion, null, null, CONTRASENA_NUEVA, CONTRASENA_NUEVA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("GERENTE_DUENO"))
                .andExpect(jsonPath("$.local").isEmpty())
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);

        assertThat(nueva.getMaxAge()).isEqualTo(10 * 60);
    }

    // --- Lo que se rechaza, sin guardar nada

    @Test
    void siNoCoincidenNoSeGuardaNada() throws Exception {
        // CP-12-03, y lo mismo con la contraseña del mozo.
        Long juan = idDe("Juan");
        Cookie sesion = sesionDeMozoConProvisoria();
        String pinHash = pinHash(juan);

        elegir(sesion, "4321", "4322", null, null)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("No coinciden"))
                // Sin cookie de sesión nueva (la de XSRF-TOKEN la deja csrf.spa() en cualquier respuesta).
                .andExpect(cookie().doesNotExist(CookieDeSesion.NOMBRE));
        elegir(sesion, "4321", "4321", CONTRASENA_NUEVA, "MiClaveNueva2")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("No coinciden"));
        elegir(sesion, "4321", null, null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("No coinciden"));

        assertThat(debeCambiar(juan)).isTrue();
        assertThat(pinHash(juan)).isEqualTo(pinHash);
    }

    @Test
    void elPinNuevoTieneQueSerDistintoDelProvisorio() throws Exception {
        // CP-12-04
        Cookie sesion = sesionDeMozoConProvisoria();

        elegir(sesion, "1234", "1234", null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El PIN nuevo tiene que ser distinto del anterior."));
        assertThat(debeCambiar(idDe("Juan"))).isTrue();
    }

    @Test
    void laContrasenaNuevaTieneQueSerDistintaDeLaProvisoria() throws Exception {
        // P-32b para el mozo, y P-29 para el Admin.
        Cookie mozo = sesionDeMozoConProvisoria();
        elegir(mozo, "4321", "4321", CONTRASENA, CONTRASENA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La contraseña nueva tiene que ser distinta de la anterior."));

        marcar(idDeEmail("admin@laesquina.test"));
        Cookie admin = ingresarConEmail("admin@laesquina.test", CONTRASENA)
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
        elegir(admin, null, null, CONTRASENA, CONTRASENA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La contraseña nueva tiene que ser distinta de la anterior."));

        // El PIN del mozo tampoco quedó guardado: va todo junto.
        assertThat(debeCambiar(idDe("Juan"))).isTrue();
    }

    @Test
    void elPinVaDeCuatroADiezDigitosYLaContrasenaDeOchoASesentaYCuatro() throws Exception {
        // CP-12-05 y CP-12-06
        Cookie sesion = sesionDeMozoConProvisoria();

        for (String pin : new String[] {"123", "12345678901", "12a4"}) {
            elegir(sesion, pin, pin, null, null).andExpect(status().isBadRequest());
        }
        for (String clave : new String[] {"1234567", "a".repeat(65)}) {
            elegir(sesion, "4321", "4321", clave, clave).andExpect(status().isBadRequest());
        }
        assertThat(debeCambiar(idDe("Juan"))).isTrue();
    }

    @Test
    void cadaRolMandaLaCredencialQueUsa() throws Exception {
        Cookie mozo = sesionDeMozoConProvisoria();
        elegir(mozo, null, null, CONTRASENA_NUEVA, CONTRASENA_NUEVA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Elegí tu PIN."));

        Long carla = idDe("Carla");
        marcar(carla);
        Cookie caja = pin(carla, "2468", null).andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
        elegir(caja, "8642", "8642", CONTRASENA_NUEVA, CONTRASENA_NUEVA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Los datos enviados no son válidos."));

        marcar(idDeEmail("admin@laesquina.test"));
        Cookie admin = ingresarConEmail("admin@laesquina.test", CONTRASENA)
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
        elegir(admin, null, null, null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Elegí tu contraseña."));
        elegir(admin, "4321", "4321", CONTRASENA_NUEVA, CONTRASENA_NUEVA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Los datos enviados no son válidos."));

        assertThat(debeCambiar(carla)).isTrue();
        assertThat(debeCambiar(idDeEmail("admin@laesquina.test"))).isTrue();
    }

    @Test
    void sinCredencialProvisoriaDa409() throws Exception {
        Long carla = idDe("Carla");
        Cookie sesion = pin(carla, "2468", null).andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);

        elegir(sesion, "8642", "8642", null, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Tu credencial no es provisoria."));
        pin(carla, "2468", null).andExpect(status().isOk());
    }

    @Test
    void elegirlaDosVecesDa409() throws Exception {
        // La segunda vez, con el token viejo que todavía dice provisoria: la marca se mira en la base.
        Cookie sesion = sesionDeMozoConProvisoria();
        elegir(sesion, "4321", "4321", null, null).andExpect(status().isOk());

        elegir(sesion, "9876", "9876", null, null).andExpect(status().isConflict());
    }

    // --- Ayudas

    /** Juan con la marca puesta: habilita el dispositivo con la contraseña y entra con el PIN. */
    private Cookie sesionDeMozoConProvisoria() throws Exception {
        Long juan = idDe("Juan");
        marcar(juan);
        Cookie dispositivo = cookieDeDispositivo("juan", CONTRASENA);
        return pin(juan, "1234", dispositivo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debeCambiarCredencial").value(true))
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
    }

    private ResultActions elegir(Cookie sesion, String pin, String pinRepetido, String password,
            String passwordRepetida) throws Exception {
        Cookie xsrf = xsrf();
        return mvc.perform(post(CREDENCIAL).cookie(sesion, xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo(pin, pinRepetido, password, passwordRepetida)));
    }

    private String cuerpo(String pin, String pinRepetido, String password, String passwordRepetida) {
        return json.writeValueAsString(new PedidoDeCredencial(pin, pinRepetido, password, passwordRepetida));
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

    private ResultActions ingresarConEmail(String email, String password) throws Exception {
        Cookie xsrf = xsrf();
        String cuerpo = json.writeValueAsString(Map.of("email", email, "password", password));
        return mvc.perform(post("/api/v1/auth/admin/login").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private Cookie xsrf() throws Exception {
        return mvc.perform(get("/api/v1/ping")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    /** Lo que hace el alta de HU-14 o el blanqueo: la credencial que tiene pasa a ser provisoria. */
    private void marcar(Long usuarioId) {
        jdbc.update("UPDATE usuario SET debe_cambiar_credencial = TRUE WHERE id = ?", usuarioId);
    }

    private boolean debeCambiar(Long usuarioId) {
        return jdbc.queryForObject("SELECT debe_cambiar_credencial FROM usuario WHERE id = ?", Boolean.class,
                usuarioId);
    }

    private String pinHash(Long usuarioId) {
        return jdbc.queryForObject("SELECT pin_hash FROM usuario WHERE id = ?", String.class, usuarioId);
    }

    private Long idDe(String nombre) {
        return jdbc.queryForObject("""
                SELECT u.id FROM usuario u JOIN local l ON l.id = u.local_id
                WHERE u.nombre = ? AND l.nombre = 'La Esquina'""", Long.class, nombre);
    }

    private Long idDeEmail(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
    }

    private record PedidoDeCredencial(String pin, String pinRepetido, String password, String passwordRepetida) {
    }
}
