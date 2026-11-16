package com.chitgo.usuarios.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.chitgo.usuarios.infraestructura.seguridad.CookieDeSesion;

/**
 * HU-16, tarea 1 de la ST-24: los Admins del Gerente/Dueño de punta a punta, con el login de verdad (el Gerente/Dueño
 * con su email) contra el MySQL del Compose y la seed. La base vuelve a la seed con lo de {@link EmpleadosHttpBase}:
 * borra los usuarios, empleados y registros de auditoría nuevos, y devuelve el Admin de la seed como estaba.
 */
class AdminsDelGerenteTest extends EmpleadosHttpBase {

    private static final String ADMINS = "/api/v1/gerente/admins";
    private static final String LOGIN = "/api/v1/auth/admin/login";
    private static final String DUENO_ESQUINA = "dueno@laesquina.test";
    private static final String DUENO_PUERTO = "dueno@elpuerto.test";
    private static final String ADMIN_ESQUINA = "admin@laesquina.test";
    private static final String PROVISORIA = "Provisoria2026";
    private static final String NO_EXISTE = "El Admin no existe";

    @Test
    void cp_31_01_elAdminNuevoEntraConLaProvisoriaYSoloPuedeElegirLaSuya() throws Exception {
        Cookie dueno = sesionConEmail(DUENO_ESQUINA);

        crear(dueno, "Admin Dos", "admin2@laesquina.test", PROVISORIA)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Admin Dos"))
                .andExpect(jsonPath("$.email").value("admin2@laesquina.test"))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.bloqueado").value(false));
        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT local_id, debe_cambiar_credencial, contrasena_provisoria FROM usuario WHERE email = ?",
                "admin2@laesquina.test");
        assertThat(fila.get("local_id")).isEqualTo(localDe("La Esquina"));
        assertThat(fila.get("debe_cambiar_credencial")).isEqualTo(true);
        assertThat(fila.get("contrasena_provisoria")).isEqualTo(false);

        Cookie admin2 = ingresar("admin2@laesquina.test", PROVISORIA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("ADMIN"))
                .andExpect(jsonPath("$.local.nombre").value("La Esquina"))
                .andExpect(jsonPath("$.debeCambiarCredencial").value(true))
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);

        // P-29: hasta elegir la suya, lo demás da 403 con CREDENCIAL_PROVISORIA.
        mvc.perform(get(EMPLEADOS).cookie(admin2))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CREDENCIAL_PROVISORIA"));

        Cookie propia = enviar(post("/api/v1/auth/credencial").cookie(admin2).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("password", "MiPropia2026", "passwordRepetida", "MiPropia2026"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debeCambiarCredencial").value(false))
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
        mvc.perform(get(EMPLEADOS).cookie(propia)).andExpect(status().isOk());
    }

    @Test
    void cp_31_02_conElAdminDeLaSeedCreaUnSegundo() throws Exception {
        Cookie dueno = sesionConEmail(DUENO_ESQUINA);
        assertThat(emails(dueno)).containsExactly(ADMIN_ESQUINA);

        crear(dueno, "Bea", "bea@laesquina.test", PROVISORIA).andExpect(status().isCreated());

        // Por nombre: "Admin La Esquina" antes que "Bea".
        assertThat(emails(dueno)).containsExactly(ADMIN_ESQUINA, "bea@laesquina.test");
        Long bea = usuarioPorEmail("bea@laesquina.test");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM empleado WHERE usuario_id = ?", Integer.class, bea))
                .isEqualTo(1);
        // No aparece entre los empleados del Admin, que son solo Mozo, Caja y Cocina.
        String empleados = mvc.perform(get(EMPLEADOS).cookie(sesionDelAdmin()))
                .andReturn().getResponse().getContentAsString();
        assertThat(empleados).doesNotContain("bea@laesquina.test", "\"Bea\"");

        List<Map<String, Object>> auditoria = auditoriaNueva();
        assertThat(auditoria).hasSize(1);
        assertThat(auditoria.get(0).get("accion")).isEqualTo("CREAR_ADMIN");
        assertThat(auditoria.get(0).get("entidad")).isEqualTo("USUARIO");
        assertThat(auditoria.get(0).get("entidad_id")).isEqualTo(bea);
        assertThat(auditoria.get(0).get("local_id")).isEqualTo(localDe("La Esquina"));
        assertThat(auditoria.get(0).get("usuario_id")).isEqualTo(usuarioPorEmail(DUENO_ESQUINA));
        assertThat(auditoria.get(0).get("valor_nuevo").toString()).doesNotContain(PROVISORIA, "$2a$");
    }

    @Test
    void cp_31_03_elEmailEsUnicoEnTodoElSistemaSinMayusculas() throws Exception {
        crear(sesionConEmail(DUENO_PUERTO), "Admin El Puerto", "admin@elpuerto.test", PROVISORIA)
                .andExpect(status().isCreated());
        Cookie esquina = sesionConEmail(DUENO_ESQUINA);
        int usuarios = cantidadDeUsuarios();

        for (String email : List.of("admin@elpuerto.test", "ADMIN@elpuerto.test")) {
            crear(esquina, "Otro", email, PROVISORIA)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("Ya hay un usuario con ese email"));
        }
        assertThat(cantidadDeUsuarios()).isEqualTo(usuarios);
    }

    @Test
    void cp_31_04_dadoDeBajaNoEntraYSuAuditoriaQueda() throws Exception {
        Cookie dueno = sesionConEmail(DUENO_ESQUINA);
        Long admin2 = id(crear(dueno, "Admin Dos", "admin2@laesquina.test", PROVISORIA));

        activo(dueno, admin2, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
        ingresar("admin2@laesquina.test", PROVISORIA).andExpect(status().isUnauthorized());

        // La baja no borra: sigue en la lista, marcado, y su historial queda.
        assertThat(JsonPath.<List<Boolean>>read(lista(dueno), "$[?(@.id == " + admin2 + ")].activo"))
                .containsExactly(false);
        assertThat(accionesNuevas()).containsExactly("CREAR_ADMIN", "BAJA_ADMIN");

        // Repetir la baja responde lo mismo y no audita.
        activo(dueno, admin2, false).andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
        assertThat(accionesNuevas()).containsExactly("CREAR_ADMIN", "BAJA_ADMIN");

        activo(dueno, admin2, true).andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(true));
        ingresar("admin2@laesquina.test", PROVISORIA).andExpect(status().isOk());
        assertThat(accionesNuevas()).containsExactly("CREAR_ADMIN", "BAJA_ADMIN", "REACTIVAR_ADMIN");
    }

    @Test
    void cp_31_05_elAdminCajaYMozoReciben403() throws Exception {
        for (Cookie sesion : List.of(sesionDelAdmin(), sesionDe("Carla", "2468"), sesionDe("Juan", "1234"))) {
            mvc.perform(get(ADMINS).cookie(sesion))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.detail").value(SIN_PERMISO));
            crear(sesion, "Intruso", "intruso@laesquina.test", PROVISORIA).andExpect(status().isForbidden());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE email = 'intruso@laesquina.test'",
                Integer.class)).isZero();
    }

    @Test
    void elDesbloqueoDejaEntrarAlAdminBloqueado() throws Exception {
        for (int i = 0; i < 5; i++) {
            ingresar(ADMIN_ESQUINA, "otraContrasena").andExpect(status().isUnauthorized());
        }
        ingresar(ADMIN_ESQUINA, CONTRASENA).andExpect(status().isUnauthorized());

        Cookie dueno = sesionConEmail(DUENO_ESQUINA);
        Long admin = usuarioPorEmail(ADMIN_ESQUINA);
        assertThat(JsonPath.<List<Boolean>>read(lista(dueno), "$[?(@.id == " + admin + ")].bloqueado"))
                .containsExactly(true);

        desbloquear(dueno, admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bloqueado").value(false));
        assertThat(jdbc.queryForObject("SELECT intentos_fallidos FROM usuario WHERE id = ?", Integer.class, admin))
                .isZero();
        ingresar(ADMIN_ESQUINA, CONTRASENA).andExpect(status().isOk());
        assertThat(accionesNuevas()).containsExactly("DESBLOQUEAR_ADMIN");

        // Desbloquear al que no está bloqueado responde 200 y no audita.
        desbloquear(dueno, admin).andExpect(status().isOk());
        assertThat(accionesNuevas()).containsExactly("DESBLOQUEAR_ADMIN");
    }

    @Test
    void elDeElPuertoNoVeNiTocaALosAdminsDeLaEsquina() throws Exception {
        Cookie puerto = sesionConEmail(DUENO_PUERTO);
        Long adminEsquina = usuarioPorEmail(ADMIN_ESQUINA);
        jdbc.update("UPDATE usuario SET intentos_fallidos = 5, bloqueado_hasta = '2038-01-01' WHERE id = ?",
                adminEsquina);
        var antes = foto();

        assertThat(emails(puerto)).isEmpty();
        activo(puerto, adminEsquina, false)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(NO_EXISTE));
        desbloquear(puerto, adminEsquina)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(NO_EXISTE));

        assertThat(foto()).isEqualTo(antes);
        assertThat(auditoriaNueva()).isEmpty();
    }

    @Test
    void unIdQueNoEsUnAdminDaElMismo404() throws Exception {
        Cookie dueno = sesionConEmail(DUENO_ESQUINA);
        for (Long id : List.of(usuarioDe("Carla"), usuarioPorEmail(DUENO_ESQUINA), 999_999L)) {
            activo(dueno, id, false)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value(NO_EXISTE));
        }
    }

    @Test
    void loQueFaltaOEstaMalDa400() throws Exception {
        Cookie dueno = sesionConEmail(DUENO_ESQUINA);
        crear(dueno, "Bea", "bea@laesquina.test", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Falta la contraseña"));
        crear(dueno, "Bea", null, PROVISORIA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Falta el email"));
        crear(dueno, "Bea", "bea@laesquina.test", "corta").andExpect(status().isBadRequest());
        crear(dueno, "Bea", "no-es-un-email", PROVISORIA).andExpect(status().isBadRequest());
        enviar(patch(ADMINS + "/" + usuarioPorEmail(ADMIN_ESQUINA) + "/activo").cookie(dueno)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Falta activo"));
        assertThat(auditoriaNueva()).isEmpty();
    }

    @Test
    void sinElHeaderDeCsrfDa403YSinSesion401() throws Exception {
        Cookie dueno = sesionConEmail(DUENO_ESQUINA);
        String alta = cuerpo(Map.of("nombre", "Bea", "email", "bea@laesquina.test", "password", PROVISORIA));

        mvc.perform(post(ADMINS).cookie(dueno).contentType(MediaType.APPLICATION_JSON).content(alta))
                .andExpect(status().isForbidden());
        mvc.perform(post(ADMINS + "/" + usuarioPorEmail(ADMIN_ESQUINA) + "/desbloquear").cookie(dueno))
                .andExpect(status().isForbidden());
        mvc.perform(get(ADMINS)).andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE email = 'bea@laesquina.test'",
                Integer.class)).isZero();
    }

    // --- Lo que se pide

    private ResultActions crear(Cookie sesion, String nombre, String email, String password) throws Exception {
        Map<String, Object> alta = new java.util.HashMap<>();
        alta.put("nombre", nombre);
        alta.put("email", email);
        alta.put("password", password);
        return enviar(post(ADMINS).cookie(sesion).contentType(MediaType.APPLICATION_JSON).content(cuerpo(alta)));
    }

    private ResultActions activo(Cookie sesion, Long id, boolean activo) throws Exception {
        return enviar(patch(ADMINS + "/" + id + "/activo").cookie(sesion).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(Map.of("activo", activo))));
    }

    private ResultActions desbloquear(Cookie sesion, Long id) throws Exception {
        return enviar(post(ADMINS + "/" + id + "/desbloquear").cookie(sesion));
    }

    private ResultActions ingresar(String email, String password) throws Exception {
        return enviar(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(Map.of("email", email, "password", password))));
    }

    private String lista(Cookie sesion) throws Exception {
        return mvc.perform(get(ADMINS).cookie(sesion)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private List<String> emails(Cookie sesion) throws Exception {
        return JsonPath.read(lista(sesion), "$[*].email");
    }

    // --- La base

    private Long usuarioPorEmail(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
    }

    private int cantidadDeUsuarios() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM usuario", Integer.class);
    }
}
