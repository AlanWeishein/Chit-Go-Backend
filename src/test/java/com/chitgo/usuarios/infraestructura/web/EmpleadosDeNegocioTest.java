package com.chitgo.usuarios.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * HU-14 de punta a punta (CP-27-01 a CP-27-06) y los casos sin número de la tarea 4 de la ST-22: aislamiento entre
 * locales, mesas, paginado y los errores como salen por HTTP. CP-10-06, CP-10-13 y la parte de login de CP-27-05 ya
 * los cubre BlanqueoYAusenciaEnElLoginTest; acá CP-27-05 suma la ausencia cargada por la ruta del Admin. Entra el Admin
 * con su email. La base queda como la seed (ver EmpleadosHttpBase).
 */
class EmpleadosDeNegocioTest extends EmpleadosHttpBase {

    private static final String YA_EXISTE = "Ya existe un usuario con ese nombre";
    private static final String DE_BAJA = "El empleado está dado de baja";
    private static final String NO_ESTA_EN_LA_LISTA = "El usuario no está en la lista de este local.";

    // --- CP-27

    @Test
    void CP_27_01_elAdminCreaAPedroConSusTurnosYMesasYPedroSaleEnElLogin() throws Exception {
        Cookie admin = sesionDelAdmin();
        Long mesa5 = mesaDe("5", "La Esquina");
        Long mesa6 = mesaDe("6", "La Esquina");

        Long pedro = id(enviar(post(EMPLEADOS).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(altaDeMozo("Pedro", "pedro", "0000")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Pedro"))
                .andExpect(jsonPath("$.rol").value("MOZO"))
                .andExpect(jsonPath("$.nombreUsuario").value("pedro"))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.debeCambiarCredencial").value(true))
                .andExpect(jsonPath("$.contrasenaProvisoria").value(true)));

        enviar(put(EMPLEADOS + "/" + pedro + "/turnos").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(List.of(turno("LUN", "12:00", "16:00"), turno("MAR", "12:00", "16:00"),
                                turno("MIE", "12:00", "16:00"), turno("JUE", "12:00", "16:00"),
                                turno("VIE", "12:00", "16:00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turnos.length()").value(5));
        enviar(put(EMPLEADOS + "/" + pedro + "/mesas").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("mesaIds", List.of(mesa5, mesa6)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesaIds", containsInAnyOrder(mesa5.intValue(), mesa6.intValue())));

        Long usuario = usuarioDelEmpleado(pedro);
        assertThat(jdbc.queryForObject("SELECT debe_cambiar_credencial FROM usuario WHERE id = ?", Boolean.class,
                usuario)).isTrue();
        assertThat(jdbc.queryForObject("SELECT contrasena_provisoria FROM usuario WHERE id = ?", Boolean.class,
                usuario)).isTrue();
        assertThat(jdbc.queryForObject("SELECT local_id FROM empleado WHERE id = ?", Long.class, pedro))
                .isEqualTo(localDe("La Esquina"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM turno WHERE empleado_id = ? AND hora_inicio = '12:00:00' AND hora_fin = '16:00:00'",
                Integer.class, pedro)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mozo_mesa WHERE empleado_id = ?", Integer.class, pedro))
                .isEqualTo(2);
        mvc.perform(get(STAFF + TOKEN_LA_ESQUINA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarios[*].nombre", hasItem("Pedro")));
    }

    @Test
    void CP_27_02_laMesa3QuedaConJuanAnaYPedro() throws Exception {
        Cookie admin = sesionDelAdmin();
        Long mesa3 = mesaDe("3", "La Esquina");
        Long pedro = crearMozo(admin, "Pedro", "pedro", "0000");

        enviar(put(EMPLEADOS + "/" + pedro + "/mesas").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("mesaIds", List.of(mesa3)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesaIds", containsInAnyOrder(mesa3.intValue())));

        List<String> mozosDeLaMesa3 = jdbc.queryForList("""
                SELECT u.nombre FROM mozo_mesa mm JOIN empleado e ON e.id = mm.empleado_id
                JOIN usuario u ON u.id = e.usuario_id WHERE mm.mesa_id = ? ORDER BY u.nombre""", String.class, mesa3);
        assertThat(mozosDeLaMesa3).containsExactly("Ana", "Juan", "Pedro");
        mvc.perform(get(EMPLEADOS + "/" + empleadoDe("Juan")).cookie(admin))
                .andExpect(jsonPath("$.mesaIds", hasItem(mesa3.intValue())));
        mvc.perform(get(EMPLEADOS + "/" + empleadoDe("Ana")).cookie(admin))
                .andExpect(jsonPath("$.mesaIds", hasItem(mesa3.intValue())));
    }

    @Test
    void CP_27_03_otroMozoConElUsuarioJuanDa409ConCualquierMayusculaYConTildePeroEnElPuertoSePuedeRepetir()
            throws Exception {
        Cookie admin = sesionDelAdmin();
        Map<String, List<Map<String, Object>>> antes = foto();

        for (String repetido : List.of("juan", "JUAN", "juán")) {
            enviar(post(EMPLEADOS).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                            .content(altaDeMozo("Otro Juan", repetido, "0000")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value(YA_EXISTE));
        }
        assertThat(foto()).isEqualTo(antes);

        // El Puerto tiene su propio "juan": el Admin de ese local repite "ana", que es de La Esquina.
        crearAdminDeElPuerto();
        Cookie adminDelPuerto = sesionConEmail("admin@elpuerto.test");
        enviar(post(EMPLEADOS).cookie(adminDelPuerto).contentType(MediaType.APPLICATION_JSON)
                        .content(altaDeMozo("Ana del Puerto", "ana", "0000")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombreUsuario").value("ana"));
        enviar(post(EMPLEADOS).cookie(adminDelPuerto).contentType(MediaType.APPLICATION_JSON)
                        .content(altaDeMozo("Otro Juan", "juan", "0000")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(YA_EXISTE));
    }

    @Test
    void CP_27_04_laBajaDePedroLoSacaDelLoginYNoLoDejaEntrarPeroSuPedidoYSuAuditoriaSiguen() throws Exception {
        Cookie admin = sesionDelAdmin();
        Long pedro = crearMozo(admin, "Pedro", "pedro", "0000");
        Long usuario = usuarioDelEmpleado(pedro);
        Cookie dispositivo = dispositivoDe(usuario);
        pin(usuario, "0000", dispositivo).andExpect(status().isOk());
        Long pedido = pedidoServidoPor(pedro, usuario);
        int auditoriaDePedro = auditoriaDe(pedro);
        assertThat(auditoriaDePedro).isPositive();

        enviar(delete(EMPLEADOS + "/" + pedro).cookie(admin)).andExpect(status().isNoContent());

        mvc.perform(get(STAFF + TOKEN_LA_ESQUINA))
                .andExpect(jsonPath("$.usuarios[*].nombre", not(hasItem("Pedro"))));
        pin(usuario, "0000", dispositivo)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(NO_ESTA_EN_LA_LISTA));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pedido WHERE id = ? AND servido_por_usuario_id = ?",
                Integer.class, pedido, usuario)).isEqualTo(1);
        // No hay un endpoint de auditoría: "la auditoría lo sigue mostrando" se prueba contando sus filas en la base.
        assertThat(auditoriaDe(pedro)).isEqualTo(auditoriaDePedro + 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE id = ? AND activo = FALSE", Integer.class,
                usuario)).isEqualTo(1);
        mvc.perform(get(EMPLEADOS + "/" + pedro).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Pedro"))
                .andExpect(jsonPath("$.activo").value(false));
    }

    @Test
    void CP_27_05_laAusenciaCargadaPorElAdminSacaAAnaYAlDiaSiguienteDeSuFechaDeVueltaVuelveSola() throws Exception {
        Cookie admin = sesionDelAdmin();
        Long ana = empleadoDe("Ana");
        Cookie dispositivo = dispositivoDe(usuarioDe("Ana"));
        Long ausencia = id(enviar(post(EMPLEADOS + "/" + ana + "/ausencias").cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("tipo", "VACACIONES", "desde", hoy().toString(),
                                "hasta", hoy().plusDays(3).toString()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("VACACIONES"))
                .andExpect(jsonPath("$.hasta").value(hoy().plusDays(3).toString())));
        assertThat(jdbc.queryForObject("SELECT creada_por_usuario_id FROM ausencia WHERE id = ?", Long.class,
                ausencia)).isEqualTo(jdbc.queryForObject("SELECT id FROM usuario WHERE email = 'admin@laesquina.test'",
                Long.class));
        enviar(patch(EMPLEADOS + "/" + ana + "/ausencias/" + ausencia).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("hasta", hoy().plusDays(1).toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasta").value(hoy().plusDays(1).toString()));
        mvc.perform(get(EMPLEADOS + "/" + ana + "/ausencias").cookie(admin))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].hasta").value(hoy().plusDays(1).toString()));
        mvc.perform(get(EMPLEADOS + "/" + ana).cookie(admin)).andExpect(jsonPath("$.ausenciaVigente").value(true));

        mvc.perform(get(STAFF + TOKEN_LA_ESQUINA))
                .andExpect(jsonPath("$.usuarios[*].nombre", not(hasItem("Ana"))));
        pin(usuarioDe("Ana"), "5678", dispositivo).andExpect(status().isNotFound());

        reloj.avanzar(Duration.ofDays(1)); // el último día de la ausencia todavía no entra
        mvc.perform(get(STAFF + TOKEN_LA_ESQUINA))
                .andExpect(jsonPath("$.usuarios[*].nombre", not(hasItem("Ana"))));
        pin(usuarioDe("Ana"), "5678", dispositivo).andExpect(status().isNotFound());

        reloj.avanzar(Duration.ofDays(1)); // al día siguiente vuelve sola
        mvc.perform(get(STAFF + TOKEN_LA_ESQUINA))
                .andExpect(jsonPath("$.usuarios[*].nombre", hasItem("Ana")));
        pin(usuarioDe("Ana"), "5678", dispositivo).andExpect(status().isOk());
    }

    @Test
    void CP_27_06_elTurnoDe20a02SeGuardaTalCual() throws Exception {
        Cookie admin = sesionDelAdmin();
        Long ana = empleadoDe("Ana");

        enviar(put(EMPLEADOS + "/" + ana + "/turnos").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(List.of(turno("LUN", "20:00", "02:00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turnos.length()").value(1))
                .andExpect(jsonPath("$.turnos[0].dia").value("LUN"))
                .andExpect(jsonPath("$.turnos[0].horaInicio").value("20:00:00"))
                .andExpect(jsonPath("$.turnos[0].horaFin").value("02:00:00"));

        mvc.perform(get(EMPLEADOS + "/" + ana).cookie(admin))
                .andExpect(jsonPath("$.turnos[0].horaInicio").value("20:00:00"))
                .andExpect(jsonPath("$.turnos[0].horaFin").value("02:00:00"));
        assertThat(jdbc.queryForList("SELECT dia_semana, hora_inicio, hora_fin FROM turno WHERE empleado_id = ?", ana))
                .hasSize(1)
                .allSatisfy(fila -> {
                    assertThat(fila.get("dia_semana")).isEqualTo("LUN");
                    assertThat(fila.get("hora_inicio").toString()).isEqualTo("20:00:00");
                    assertThat(fila.get("hora_fin").toString()).isEqualTo("02:00:00");
                });
    }

    // --- Sin número

    @Test
    void elAdminDeLaEsquinaNoVeNiTocaAUnEmpleadoDeElPuertoYNoCambiaNada() throws Exception {
        Cookie admin = sesionDelAdmin();
        Long puerto = empleadoDeElPuerto();
        Long mesaEsquina = mesaDe("1", "La Esquina");
        Map<String, List<Map<String, Object>>> antes = foto();
        String altaJson = cuerpo(Map.of("nombre", "Otro", "nombreUsuario", "otro"));
        String ausenciaJson = cuerpo(Map.of("tipo", "FRANCO", "desde", hoy().toString()));

        List<ResultActions> pedidos = List.of(
                mvc.perform(get(EMPLEADOS + "/" + puerto).cookie(admin)),
                enviar(put(EMPLEADOS + "/" + puerto).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(altaJson)),
                enviar(delete(EMPLEADOS + "/" + puerto).cookie(admin)),
                enviar(post(EMPLEADOS + "/" + puerto + "/pin").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\": \"0000\"}")),
                enviar(put(EMPLEADOS + "/" + puerto + "/turnos").cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("[]")),
                enviar(put(EMPLEADOS + "/" + puerto + "/mesas").cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("mesaIds", List.of(mesaEsquina))))),
                mvc.perform(get(EMPLEADOS + "/" + puerto + "/ausencias").cookie(admin)),
                enviar(post(EMPLEADOS + "/" + puerto + "/ausencias").cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(ausenciaJson)),
                enviar(patch(EMPLEADOS + "/" + puerto + "/ausencias/1").cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("hasta", hoy().toString())))));
        for (ResultActions pedido : pedidos) {
            pedido.andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("El empleado no existe"))
                    .andExpect(content().string(not(containsString("Puerto"))));
        }

        mvc.perform(get(EMPLEADOS + "?size=100").cookie(admin))
                .andExpect(jsonPath("$.contenido[*].id", not(hasItem(puerto.intValue()))));
        assertThat(foto()).isEqualTo(antes);
        assertThat(auditoriaNueva()).isEmpty();
    }

    @Test
    void unaMesaDeElPuertoDa404YNoSeGuardaNingunaMesa() throws Exception {
        Cookie admin = sesionDelAdmin();
        Long pedro = crearMozo(admin, "Pedro", "pedro", "0000");
        Long delLocal = mesaDe("5", "La Esquina");
        Long delPuerto = mesaDe("1", "El Puerto");
        Map<String, List<Map<String, Object>>> antes = foto();
        int auditoriasAntes = auditoriaNueva().size();

        enviar(put(EMPLEADOS + "/" + pedro + "/mesas").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("mesaIds", List.of(delLocal, delPuerto)))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("La mesa no existe"))
                .andExpect(content().string(not(containsString("Puerto"))));

        assertThat(foto()).isEqualTo(antes);
        assertThat(auditoriaNueva()).hasSize(auditoriasAntes);
    }

    @Test
    void conMasEmpleadosQueElTamanoLaSegundaPaginaTraeElRestoEnOrden() throws Exception {
        Cookie admin = sesionDelAdmin();
        crearMozo(admin, "Dora", "dora", "0000");
        crearMozo(admin, "Beto", "beto", "0000");
        crearMozo(admin, "Eli", "eli", "0000");
        // Con la seed: Ana, Beto, Carla, Dora, Eli, Hugo, Juan.

        mvc.perform(get(EMPLEADOS + "?page=0&size=3").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.totalElementos").value(7))
                .andExpect(jsonPath("$.totalPaginas").value(3))
                .andExpect(jsonPath("$.contenido[*].nombre", contains("Ana", "Beto", "Carla")));
        mvc.perform(get(EMPLEADOS + "?page=1&size=3").cookie(admin))
                .andExpect(jsonPath("$.contenido[*].nombre", contains("Dora", "Eli", "Hugo")));
        mvc.perform(get(EMPLEADOS + "?page=2&size=3").cookie(admin))
                .andExpect(jsonPath("$.contenido[*].nombre", contains("Juan")));
        mvc.perform(get(EMPLEADOS + "?page=3&size=3").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(0));
        mvc.perform(get(EMPLEADOS).cookie(admin))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.contenido.length()").value(7));
    }

    @Test
    void unaPaginaOUnTamanoFueraDeRangoDa400() throws Exception {
        Cookie admin = sesionDelAdmin();
        for (String consulta : List.of("?size=0", "?size=101", "?page=-1")) {
            mvc.perform(get(EMPLEADOS + consulta).cookie(admin))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("La página o el tamaño no son válidos"));
        }
    }

    @Test
    void unaPaginaQueNoEsUnNumeroDa400() throws Exception {
        // Estaba en "Abierto": sin probar.
        mvc.perform(get(EMPLEADOS + "?page=abc").cookie(sesionDelAdmin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void losErroresSalenPorHttpConSuMensaje() throws Exception {
        Cookie admin = sesionDelAdmin();
        Long pedro = crearMozo(admin, "Pedro", "pedro", "0000");
        String ruta = EMPLEADOS + "/" + pedro;

        enviar(post(EMPLEADOS).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("nombre", "X", "rol", "GERENTE", "pin", "0000"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El rol tiene que ser Mozo, Caja o Cocina"));
        enviar(post(EMPLEADOS).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("rol", "CAJA", "pin", "0000"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Falta el nombre"));
        enviar(post(EMPLEADOS).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("nombre", "X", "rol", "CAJA", "pin", "12"))))
                .andExpect(status().isBadRequest());
        enviar(put(ruta + "/turnos").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(List.of(turno("LUNES", "12:00", "16:00")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El día tiene que ser LUN, MAR, MIE, JUE, VIE, SAB o DOM"));
        enviar(put(ruta + "/turnos").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(List.of(turno("LUN", "12:00", "12:00")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El turno no puede empezar y terminar a la misma hora"));
        enviar(put(ruta + "/turnos").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(List.of(turno("LUN", "12:00", "16:00"), turno("LUN", "15:00", "18:00")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Los turnos del lunes se pisan"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM turno WHERE empleado_id = ?", Integer.class, pedro))
                .isZero();
        enviar(post(ruta + "/ausencias").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("tipo", "FRANCO", "desde", hoy().toString(),
                                "hasta", hoy().minusDays(1).toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La ausencia no puede terminar antes de empezar"));

        // Dado de baja: de solo lectura.
        enviar(delete(ruta).cookie(admin)).andExpect(status().isNoContent());
        enviar(delete(ruta).cookie(admin)).andExpect(status().isNoContent());
        List<ResultActions> escrituras = List.of(
                enviar(put(ruta).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("nombre", "Otro", "nombreUsuario", "pedro")))),
                enviar(put(ruta + "/turnos").cookie(admin).contentType(MediaType.APPLICATION_JSON).content("[]")),
                enviar(put(ruta + "/mesas").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mesaIds\": []}")),
                enviar(post(ruta + "/pin").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\": \"1111\"}")),
                enviar(post(ruta + "/ausencias").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("tipo", "FRANCO", "desde", hoy().toString())))));
        for (ResultActions escritura : escrituras) {
            escritura.andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(DE_BAJA));
        }
        mvc.perform(get(ruta + "/ausencias").cookie(admin)).andExpect(status().isOk());
        assertThat(accionesNuevas().stream().filter("BAJA_EMPLEADO"::equals).count()).isEqualTo(1);
    }

    // --- Ayudas

    private Map<String, String> turno(String dia, String inicio, String fin) {
        return Map.of("dia", dia, "horaInicio", inicio, "horaFin", fin);
    }

    private Long crearMozo(Cookie admin, String nombre, String nombreUsuario, String pin) throws Exception {
        return id(enviar(post(EMPLEADOS).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(altaDeMozo(nombre, nombreUsuario, pin)))
                .andExpect(status().isCreated()));
    }

    /** En la seed El Puerto solo tiene al dueño y a Juan: un Admin para el caso, con la clave del de La Esquina. */
    private void crearAdminDeElPuerto() {
        jdbc.update("""
                INSERT INTO usuario (local_id, rol_id, nombre, nombre_usuario, email, hash_password, pin_hash,
                debe_cambiar_credencial)
                SELECT (SELECT id FROM local WHERE nombre = 'El Puerto'), rol_id, 'Admin El Puerto', NULL,
                'admin@elpuerto.test', hash_password, NULL, FALSE
                FROM usuario WHERE email = 'admin@laesquina.test'""");
    }

    /** Una cuenta con un pedido servido por Pedro, armados por JDBC: el Mozo no tiene cómo crearlos por HTTP. */
    private Long pedidoServidoPor(Long empleadoId, Long usuarioId) {
        Long local = localDe("La Esquina");
        jdbc.update("INSERT INTO cuenta (local_id, mesa_id, mozo_id, estado) VALUES (?, ?, ?, 'ABIERTA')",
                local, mesaDe("1", "La Esquina"), empleadoId);
        Long cuenta = jdbc.queryForObject("SELECT MAX(id) FROM cuenta", Long.class);
        jdbc.update("INSERT INTO cliente_sesion (local_id, cuenta_id, token_sesion, es_iniciador) VALUES (?, ?, ?, TRUE)",
                local, cuenta, UUID.randomUUID().toString());
        Long sesion = jdbc.queryForObject("SELECT MAX(id) FROM cliente_sesion", Long.class);
        jdbc.update("""
                INSERT INTO pedido (local_id, cuenta_id, cliente_sesion_id, uuid_cliente, estado, servido_por_usuario_id,
                servido_en) VALUES (?, ?, ?, ?, 'SERVIDO', ?, CURRENT_TIMESTAMP)""",
                local, cuenta, sesion, UUID.randomUUID().toString(), usuarioId);
        return jdbc.queryForObject("SELECT MAX(id) FROM pedido", Long.class);
    }

    private int auditoriaDe(Long empleadoId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM registro_auditoria WHERE entidad = 'EMPLEADO' AND entidad_id = ?",
                Integer.class, empleadoId);
    }
}
