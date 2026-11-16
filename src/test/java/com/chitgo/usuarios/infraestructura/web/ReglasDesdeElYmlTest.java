package com.chitgo.usuarios.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.chitgo.usuarios.infraestructura.seguridad.CookieDeSesion;

/**
 * Con otros números en chitgo.reglas, el back de punta a punta usa esos y no los de hoy: con 3 intentos, Carla
 * queda bloqueada al tercer PIN mal, y su sesión de Caja dura dos horas. Contra el MySQL del Compose, como
 * IngresoConPinTest, y dejando los usuarios como en la seed. Arma su propio contexto de Spring por las propiedades.
 */
@SpringBootTest(properties = {
        "chitgo.reglas.intentos-para-bloquear=3",
        "chitgo.seguridad.login-email.solicitudes=2",
        "chitgo.reglas.sesion-de-caja=2h"})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ReglasDesdeElYmlTest {

    private static final String PIN = "/api/v1/auth/pin";
    private static final String TOKEN_LA_ESQUINA = "83f39b25-deac-4fde-80cc-4cfdf8117713";
    private static final String BLOQUEADO = "Tu usuario está bloqueado. Pedile al Admin que te blanquee el PIN.";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void dejarLosUsuariosComoEnLaSeed() {
        jdbc.update("UPDATE usuario SET intentos_fallidos = 0, bloqueado_hasta = NULL, activo = TRUE");
    }

    @Test
    void conTresIntentosParaBloquearCarlaQuedaBloqueadaAlTercero() throws Exception {
        Long carla = carla();

        ingresar(carla, "0001").andExpect(status().isUnauthorized());
        ingresar(carla, "0002").andExpect(status().isUnauthorized());
        ingresar(carla, "0003")
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.detail").value(BLOQUEADO));
        ingresar(carla, "2468").andExpect(status().isLocked());

        assertThat(jdbc.queryForObject("SELECT intentos_fallidos FROM usuario WHERE id = ?", Integer.class, carla))
                .isEqualTo(3);
    }

    @Test
    void conDosHorasDeSesionDeCajaLaCookieDuraDosHoras() throws Exception {
        Cookie sesion = ingresar(carla(), "2468")
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);

        assertThat(sesion).isNotNull();
        assertThat(sesion.getMaxAge()).isEqualTo(2 * 60 * 60);
    }

    /** Carla es de Caja: entra con PIN sin la cookie de dispositivo, que es solo del mozo. */
    private ResultActions ingresar(Long usuarioId, String pin) throws Exception {
        Cookie xsrf = mvc.perform(get("/api/v1/ping")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        String cuerpo = """
                {"tokenStaff": "%s", "usuarioId": %d, "pin": "%s"}""".formatted(TOKEN_LA_ESQUINA, usuarioId, pin);
        return mvc.perform(post(PIN).cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private Long carla() {
        return jdbc.queryForObject("""
                SELECT u.id FROM usuario u JOIN local l ON l.id = u.local_id
                WHERE u.nombre = 'Carla' AND l.nombre = 'La Esquina'""", Long.class);
    }
}
