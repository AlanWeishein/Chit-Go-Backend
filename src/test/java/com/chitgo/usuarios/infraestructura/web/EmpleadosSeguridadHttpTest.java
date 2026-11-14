package com.chitgo.usuarios.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Quién puede y la auditoría de HU-14 por HTTP (tarea 4 de la ST-22): CP-27-07, el 403 de Juan, Carla y el
 * Gerente/Dueño en cada ruta de /admin/empleados (también la del blanqueo), el 401 sin sesión, el CSRF, y que la
 * auditoría nunca lleva un PIN, una contraseña ni un hash.
 */
class EmpleadosSeguridadHttpTest extends EmpleadosHttpBase {

    @Test
    void CP_27_07_juanMandaElAltaDeUnEmpleadoYRecibe403() throws Exception {
        Cookie juan = sesionDe("Juan", "1234");
        sesionDelAdmin();
        Map<String, List<Map<String, Object>>> antes = foto();

        enviar(post(EMPLEADOS).cookie(juan).contentType(MediaType.APPLICATION_JSON)
                        .content(altaDeMozo("Pedro", "pedro", "0000")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(SIN_PERMISO));

        assertThat(foto()).isEqualTo(antes);
        assertThat(auditoriaNueva()).isEmpty();
    }

    @Test
    void ningunaRutaDeEmpleadosDejaPasarAJuanACarlaNiAlGerenteYNingunaCambiaNada() throws Exception {
        Long ana = empleadoDe("Ana");
        Cookie juan = sesionDe("Juan", "1234");
        Cookie carla = sesionDe("Carla", "2468");
        Cookie gerente = sesionConEmail("dueno@laesquina.test");
        Map<String, List<Map<String, Object>>> antes = foto();

        for (Cookie sesion : List.of(juan, carla, gerente)) {
            for (MockHttpServletRequestBuilder ruta : rutas(ana)) {
                enviar(ruta.cookie(sesion))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.detail").value(SIN_PERMISO));
            }
        }

        assertThat(foto()).isEqualTo(antes);
        assertThat(auditoriaNueva()).isEmpty();
    }

    @Test
    void sinSesionTodasLasRutasDan401() throws Exception {
        Map<String, List<Map<String, Object>>> antes = foto();

        for (MockHttpServletRequestBuilder ruta : rutas(empleadoDe("Ana"))) {
            enviar(ruta).andExpect(status().isUnauthorized());
        }

        assertThat(foto()).isEqualTo(antes);
    }

    @Test
    void sinElHeaderDeCsrfLasRutasQueEscribenDan403YNoCambiaNada() throws Exception {
        Cookie admin = sesionDelAdmin();
        Map<String, List<Map<String, Object>>> antes = foto();

        for (MockHttpServletRequestBuilder ruta : rutas(empleadoDe("Ana"))) {
            if (esLectura(ruta)) {
                continue;
            }
            // Con la sesión del Admin, pero sin la cookie ni el header de CSRF.
            mvc.perform(ruta.cookie(admin)).andExpect(status().isForbidden());
        }

        assertThat(foto()).isEqualTo(antes);
        assertThat(auditoriaNueva()).isEmpty();
    }

    @Test
    void ningunRegistroDeAuditoriaTieneUnPinUnaContrasenaNiUnHash() throws Exception {
        Cookie admin = sesionDelAdmin();
        String pinDelAlta = "4097";
        String contrasenaDelAlta = "ClaveSecreta2026";
        String pinBlanqueado = "6153";
        Long pedro = id(enviar(post(EMPLEADOS).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("nombre", "Pedro", "rol", "MOZO", "pin", pinDelAlta,
                                "nombreUsuario", "pedro", "password", contrasenaDelAlta))))
                .andExpect(status().isCreated()));
        String ruta = EMPLEADOS + "/" + pedro;
        enviar(put(ruta).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(Map.of("nombre", "Pedro P", "nombreUsuario", "pedrop"))))
                .andExpect(status().isOk());
        enviar(put(ruta + "/turnos").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(List.of(Map.of("dia", "LUN", "horaInicio", "12:00", "horaFin", "16:00")))))
                .andExpect(status().isOk());
        enviar(put(ruta + "/mesas").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(Map.of("mesaIds", List.of(mesaDe("5", "La Esquina"))))))
                .andExpect(status().isOk());
        Long ausencia = id(enviar(post(ruta + "/ausencias").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("tipo", "FRANCO", "desde", hoy().toString()))))
                .andExpect(status().isCreated()));
        enviar(patch(ruta + "/ausencias/" + ausencia).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(Map.of("hasta", hoy().plusDays(2).toString()))))
                .andExpect(status().isOk());
        enviar(post(ruta + "/pin").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(Map.of("pin", pinBlanqueado))))
                .andExpect(status().isOk());
        enviar(delete(ruta).cookie(admin)).andExpect(status().isNoContent());

        assertThat(accionesNuevas()).containsExactlyInAnyOrder("CREAR_EMPLEADO", "EDITAR_EMPLEADO", "CAMBIAR_TURNOS",
                "CAMBIAR_MESAS", "CREAR_AUSENCIA", "EDITAR_AUSENCIA", "BLANQUEAR_PIN", "BAJA_EMPLEADO");
        for (Map<String, Object> registro : auditoriaNueva()) {
            String valores = registro.get("valor_anterior") + " " + registro.get("valor_nuevo");
            assertThat(valores).as(String.valueOf(registro.get("accion")))
                    .doesNotContain(pinDelAlta, contrasenaDelAlta, pinBlanqueado, "$2a$", "$2b$", "$2y$")
                    .doesNotContainIgnoringCase("hash")
                    .doesNotContainIgnoringCase("password")
                    .doesNotContainIgnoringCase("contrasena\"")
                    .doesNotContain("\"pin\"");
        }
    }

    // --- Ayudas

    /** Una de cada ruta de /admin/empleados, con un cuerpo válido, para que lo que corte sea el permiso y no el 400. */
    private List<MockHttpServletRequestBuilder> rutas(Long empleadoId) {
        String ruta = EMPLEADOS + "/" + empleadoId;
        MediaType json = MediaType.APPLICATION_JSON;
        List<MockHttpServletRequestBuilder> rutas = new ArrayList<>();
        rutas.add(get(EMPLEADOS));
        rutas.add(get(ruta));
        rutas.add(post(EMPLEADOS).contentType(json).content(altaDeMozo("Pedro", "pedro", "0000")));
        rutas.add(put(ruta).contentType(json).content(cuerpo(Map.of("nombre", "Otra", "nombreUsuario", "otra"))));
        rutas.add(delete(ruta));
        rutas.add(post(ruta + "/pin").contentType(json).content("{\"pin\": \"0000\"}"));
        rutas.add(put(ruta + "/turnos").contentType(json).content("[]"));
        rutas.add(put(ruta + "/mesas").contentType(json).content("{\"mesaIds\": []}"));
        rutas.add(get(ruta + "/ausencias"));
        rutas.add(post(ruta + "/ausencias").contentType(json)
                .content(cuerpo(Map.of("tipo", "FRANCO", "desde", hoy().toString()))));
        rutas.add(patch(ruta + "/ausencias/1").contentType(json)
                .content(cuerpo(Map.of("hasta", hoy().toString()))));
        return rutas;
    }

    private boolean esLectura(MockHttpServletRequestBuilder ruta) {
        return "GET".equals(ruta.buildRequest(new org.springframework.mock.web.MockServletContext()).getMethod());
    }
}
