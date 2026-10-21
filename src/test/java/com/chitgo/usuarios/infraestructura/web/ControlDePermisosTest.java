package com.chitgo.usuarios.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.chitgo.usuarios.dominio.puertos.EmisorDeHabilitaciones;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeDispositivo;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeSesion;

/**
 * Los permisos del rol de punta a punta: login con PIN, el filtro arma las autoridades con PermisosPorRol y el
 * {@code @PreAuthorize} del endpoint las mira, con la cola de aprobación de HU-07, que pide el permiso que en V2
 * tiene solo Caja. Contra el MySQL del Compose y los usuarios de R__datos_de_prueba.sql, sin
 * {@code @Transactional}, por lo mismo que IngresoConPinTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ControlDePermisosTest {

    private static final String RUTA = "/api/v1/caja/pedidos";
    private static final String TOKEN_LA_ESQUINA = "83f39b25-deac-4fde-80cc-4cfdf8117713";
    private static final String SIN_PERMISO = "No tenés permiso para hacer esto.";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EmisorDeHabilitaciones habilitaciones;

    @AfterEach
    void dejarLosUsuariosComoEnLaSeed() {
        jdbc.update("""
                UPDATE usuario SET debe_cambiar_credencial = FALSE, intentos_fallidos = 0, bloqueado_hasta = NULL,
                activo = TRUE""");
    }

    @Test
    void cajaTieneElPermisoYEntra() throws Exception {
        mvc.perform(get(RUTA).cookie(sesionDe("Carla", "2468")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void elMozoNoTieneElPermiso() throws Exception {
        sinPermiso(mvc.perform(get(RUTA).cookie(sesionDe("Juan", "1234"))));
    }

    @Test
    void cocinaTampocoTieneElPermiso() throws Exception {
        sinPermiso(mvc.perform(get(RUTA).cookie(sesionDe("Hugo", "1357"))));
    }

    @Test
    void sinSesionDa401() throws Exception {
        mvc.perform(get(RUTA))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("No hay una sesión iniciada, o venció."));
    }

    @Test
    void conLaCredencialProvisoriaDaElOtro403AunqueTengaElPermiso() throws Exception {
        // El 403 del primer ingreso sale antes de llegar al endpoint y trae su código: así se distingue del de
        // arriba, que no lo trae.
        jdbc.update("UPDATE usuario SET debe_cambiar_credencial = TRUE WHERE id = ?", idDe("Carla"));

        mvc.perform(get(RUTA).cookie(sesionDe("Carla", "2468")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CREDENCIAL_PROVISORIA"));
    }

    /**
     * El 403 por falta de permiso, y no el de la credencial provisoria: ese también es 403, pero lleva el código
     * CREDENCIAL_PROVISORIA y otro mensaje. La seed tiene a todos con la credencial propia.
     */
    private void sinPermiso(ResultActions respuesta) throws Exception {
        respuesta.andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(SIN_PERMISO))
                .andExpect(jsonPath("$.codigo").doesNotExist());
    }

    /**
     * Entra con el PIN como lo hace el front, siempre con la cookie de dispositivo del usuario, como en
     * IngresoConPinTest: el mozo la necesita y a Caja y Cocina no les cambia nada.
     */
    private Cookie sesionDe(String nombre, String pin) throws Exception {
        Long usuarioId = idDe(nombre);
        Cookie xsrf = mvc.perform(get("/api/v1/ping")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        Cookie dispositivo = new Cookie(CookieDeDispositivo.NOMBRE, habilitaciones.emitir(usuarioId));
        String cuerpo = """
                {"tokenStaff": "%s", "usuarioId": %d, "pin": "%s"}""".formatted(TOKEN_LA_ESQUINA, usuarioId, pin);
        Cookie sesion = mvc.perform(post("/api/v1/auth/pin").cookie(xsrf, dispositivo)
                        .header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
        assertThat(sesion).isNotNull();
        return sesion;
    }

    @Test
    void elMozoNoPuedeVerNiCambiarPedidosDeCocina() throws Exception {
        Cookie sesion = sesionDe("Juan", "1234");
        sinPermiso(mvc.perform(get("/api/v1/cocina/pedidos").cookie(sesion)));
        Cookie xsrf = mvc.perform(get("/api/v1/ping")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        for (String accion : new String[] {"en-preparacion", "listo"}) {
            sinPermiso(mvc.perform(post("/api/v1/cocina/pedidos/999999/" + accion)
                    .cookie(sesion, xsrf).header("X-XSRF-TOKEN", xsrf.getValue())));
        }
    }

    @Test
    void cocinaEntraASuCola() throws Exception {
        mvc.perform(get("/api/v1/cocina/pedidos").cookie(sesionDe("Hugo", "1357")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enCola").isArray());
    }

    private Long idDe(String nombre) {
        return jdbc.queryForObject("""
                SELECT u.id FROM usuario u JOIN local l ON l.id = u.local_id
                WHERE u.nombre = ? AND l.nombre = 'La Esquina'""", Long.class, nombre);
    }
}
