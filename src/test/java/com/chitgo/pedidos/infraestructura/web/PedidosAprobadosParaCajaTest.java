package com.chitgo.pedidos.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import com.chitgo.compartido.dominio.RelojDePrueba;
import com.chitgo.usuarios.dominio.puertos.EmisorDeHabilitaciones;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeDispositivo;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeSesion;

/**
 * ST-17, tarea 1: {@code GET /caja/pedidos/aprobados}, lo que Caja ve en la P-36. Los pedidos se arman por el camino
 * del cliente y se aprueban con Carla; Hugo los empieza. Contra el MySQL del Compose y R__datos_de_prueba.sql, sin
 * {@code @Transactional}, como AprobacionDePedidosTest. Cada test borra lo que dejó.
 *
 * <p>El aviso a {@code caja/pedidos} cuando Cocina empieza o termina un pedido no se mira por el WebSocket: lo cubre
 * ColaDeCocinaServiceTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PedidosAprobadosParaCajaTest {

    private static final String APROBADOS = "/api/v1/caja/pedidos/aprobados";
    private static final String TOKEN_LA_ESQUINA = "83f39b25-deac-4fde-80cc-4cfdf8117713";
    private static final String MESA_1_ESQUINA = "d4964b6b-fdae-4b4f-83e9-1c1e78a21723";
    private static final String MESA_2_ESQUINA = "7ac79c59-b48d-4db2-8365-5c6a6565d9f4";
    private static final String MESA_1_PUERTO = "ab0124a8-6033-4d73-a1f6-3513140e62f7";
    private static final String SIN_PERMISO = "No tenés permiso para hacer esto.";
    /** Lo que se borra al terminar, en el orden de las FK. */
    private static final List<String> TABLAS_DE_LA_PRUEBA =
            List.of("registro_auditoria", "item_pedido", "pedido", "cliente_sesion", "aviso", "cuenta");

    @TestConfiguration
    static class ConReloj {

        @Bean
        @Primary
        RelojDePrueba relojDePrueba() {
            // Sin fracciones de segundo, como guarda resuelto_en.
            return new RelojDePrueba(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EmisorDeHabilitaciones habilitaciones;

    @Autowired
    private RelojDePrueba reloj;

    private final Map<String, Long> ultimoIdAntes = new HashMap<>();

    @BeforeEach
    void anotarLoQueHabia() {
        for (String tabla : TABLAS_DE_LA_PRUEBA) {
            ultimoIdAntes.put(tabla, jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM " + tabla, Long.class));
        }
    }

    @AfterEach
    void dejarLaBaseComoEnLaSeed() {
        for (String tabla : TABLAS_DE_LA_PRUEBA) {
            jdbc.update("DELETE FROM " + tabla + " WHERE id > ?", ultimoIdAntes.get(tabla));
        }
    }

    // --- Qué trae

    @Test
    void carlaVeLosSinEmpezarYLosEnPreparacionYNoLosPendientesNiLosListos() throws Exception {
        String mesa1 = abrirMesa(MESA_1_ESQUINA);
        String mesa2 = abrirMesa(MESA_2_ESQUINA);
        Cookie carla = sesionDe("Carla", "2468");
        Cookie hugo = sesionDe("Hugo", "1357");
        Long milanesa = plato("Milanesa napolitana", "La Esquina");

        Long sinEmpezar = pedir(mesa1, milanesa, 2, "sin cebolla");
        Long enPreparacion = pedir(mesa2, milanesa, 1, null);
        Long listo = pedir(mesa2, milanesa, 1, null);
        Long pendiente = pedir(mesa1, milanesa, 1, null);
        Long rechazado = pedir(mesa1, milanesa, 1, null);
        aprobar(carla, sinEmpezar);
        aprobar(carla, enPreparacion);
        aprobar(carla, listo);
        cocina(hugo, enPreparacion, "en-preparacion");
        cocina(hugo, listo, "en-preparacion");
        cocina(hugo, listo, "listo");
        rechazar(carla, rechazado);

        Map<String, List<Map<String, Object>>> aprobados = aprobadosDeEstaPrueba(carla);

        assertThat(ids(aprobados.get("sinEmpezar"))).containsExactly(sinEmpezar);
        assertThat(ids(aprobados.get("enPreparacion"))).containsExactly(enPreparacion);
        assertThat(ids(aprobados.get("sinEmpezar"))).doesNotContain(pendiente, listo, rechazado);
        assertThat(ids(aprobados.get("enPreparacion"))).doesNotContain(pendiente, listo, rechazado);

        Map<String, Object> primero = aprobados.get("sinEmpezar").get(0);
        assertThat(primero).containsEntry("mesa", "1").containsEntry("estado", "APROBADO")
                .containsEntry("prioritario", false);
        assertThat(primero.get("aprobadoEn")).isEqualTo(reloj.ahora().toString());
        assertThat(primero.get("enPreparacionEn")).isNull();
        assertThat(items(primero)).singleElement().satisfies(item -> assertThat(item)
                .containsEntry("plato", "Milanesa napolitana")
                .containsEntry("cantidad", 2)
                .containsEntry("observacion", "sin cebolla"));

        Map<String, Object> preparandose = aprobados.get("enPreparacion").get(0);
        assertThat(preparandose).containsEntry("mesa", "2").containsEntry("estado", "EN_PREPARACION");
        assertThat(preparandose.get("enPreparacionEn")).isEqualTo(reloj.ahora().toString());
    }

    @Test
    void sinPedidosDelTestLasDosListasVienenVaciasConOk() throws Exception {
        Cookie carla = sesionDe("Carla", "2468");

        mvc.perform(get(APROBADOS).cookie(carla))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinEmpezar").isArray())
                .andExpect(jsonPath("$.enPreparacion").isArray());

        Map<String, List<Map<String, Object>>> aprobados = aprobadosDeEstaPrueba(carla);
        assertThat(aprobados.get("sinEmpezar")).isEmpty();
        assertThat(aprobados.get("enPreparacion")).isEmpty();
    }

    // --- El orden de la cola de cocina

    @Test
    void elPriorizadoVaPrimeroAunqueHayaLlegadoDespuesYAlSacarleLaPrioridadVuelveASuLugar() throws Exception {
        String mesa1 = abrirMesa(MESA_1_ESQUINA);
        Cookie carla = sesionDe("Carla", "2468");
        Long milanesa = plato("Milanesa napolitana", "La Esquina");
        Long primero = pedir(mesa1, milanesa, 1, null);
        Long segundo = pedir(mesa1, milanesa, 1, null);
        aprobar(carla, primero);
        reloj.avanzar(Duration.ofMinutes(2));
        aprobar(carla, segundo);

        assertThat(ids(aprobadosDeEstaPrueba(carla).get("sinEmpezar"))).containsExactly(primero, segundo);

        prioridad(carla, segundo, true);
        Map<String, List<Map<String, Object>>> conPrioridad = aprobadosDeEstaPrueba(carla);
        assertThat(ids(conPrioridad.get("sinEmpezar"))).containsExactly(segundo, primero);
        assertThat(conPrioridad.get("sinEmpezar").get(0)).containsEntry("prioritario", true);

        prioridad(carla, segundo, false);
        assertThat(ids(aprobadosDeEstaPrueba(carla).get("sinEmpezar"))).containsExactly(primero, segundo);
    }

    @Test
    void sinPrioridadMandaElOrdenEnQueSeAprobo() throws Exception {
        String mesa1 = abrirMesa(MESA_1_ESQUINA);
        Cookie carla = sesionDe("Carla", "2468");
        Long milanesa = plato("Milanesa napolitana", "La Esquina");
        Long llegoPrimero = pedir(mesa1, milanesa, 1, null);
        Long llegoSegundo = pedir(mesa1, milanesa, 1, null);
        aprobar(carla, llegoSegundo);
        reloj.avanzar(Duration.ofMinutes(1));
        aprobar(carla, llegoPrimero);

        assertThat(ids(aprobadosDeEstaPrueba(carla).get("sinEmpezar"))).containsExactly(llegoSegundo, llegoPrimero);
    }

    // --- Otro local

    @Test
    void unPedidoAprobadoDeElPuertoNoLeApareceACarla() throws Exception {
        // RNF-10
        String mesaDelPuerto = abrirMesa(MESA_1_PUERTO);
        Long delPuerto = pedir(mesaDelPuerto, plato("Pizza muzzarella", "El Puerto"), 1, null);
        // No hay una Caja de El Puerto en la seed: se lo deja aprobado a mano, como un Caja de ese local.
        jdbc.update("UPDATE pedido SET estado = 'APROBADO', resuelto_en = ?, resuelto_por_usuario_id = ? WHERE id = ?",
                Timestamp.from(reloj.ahora()), idDe("Carla"), delPuerto);
        Cookie carla = sesionDe("Carla", "2468");

        Map<String, List<Map<String, Object>>> aprobados = aprobadosDeEstaPrueba(carla);

        assertThat(ids(aprobados.get("sinEmpezar"))).doesNotContain(delPuerto);
        assertThat(ids(aprobados.get("enPreparacion"))).doesNotContain(delPuerto);
        assertThat(aprobados.get("sinEmpezar")).isEmpty();
    }

    // --- Quién puede

    @Test
    void sinSesionDa401() throws Exception {
        mvc.perform(get(APROBADOS))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void hugoJuanYElAdminDan403() throws Exception {
        sinPermiso(mvc.perform(get(APROBADOS).cookie(sesionDe("Hugo", "1357"))));
        sinPermiso(mvc.perform(get(APROBADOS).cookie(sesionDe("Juan", "1234"))));
        sinPermiso(mvc.perform(get(APROBADOS).cookie(sesionDelAdmin())));
    }

    // --- Ayudas: el cliente

    /** El celular escanea el QR de una mesa libre y abre la cuenta: queda como iniciador. */
    private String abrirMesa(String tokenQr) throws Exception {
        Cookie xsrf = xsrf();
        String respuesta = mvc.perform(post("/api/v1/public/mesas/" + tokenQr + "/sesiones").cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.esIniciador").value(true))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(respuesta, "$.tokenSesion");
    }

    /** Manda un pedido de un plato con la sesión del iniciador. Sale con la hora del reloj de prueba. */
    private Long pedir(String tokenSesion, Long platoId, int cantidad, String observacion) throws Exception {
        Cookie xsrf = xsrf();
        String observacionJson = observacion == null ? "null" : "\"" + observacion + "\"";
        String cuerpo = """
                {"uuidCliente": "%s", "items": [{"platoId": %d, "cantidad": %d, "observacion": %s}]}"""
                .formatted(UUID.randomUUID(), platoId, cantidad, observacionJson);
        String respuesta = mvc.perform(post("/api/v1/public/pedidos").cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue()).header("X-Session-Token", tokenSesion)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
    }

    // --- Ayudas: Caja y Cocina

    private void aprobar(Cookie caja, Long pedidoId) throws Exception {
        Cookie xsrf = xsrf();
        mvc.perform(post("/api/v1/caja/pedidos/" + pedidoId + "/aprobar").cookie(caja, xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isNoContent());
    }

    private void rechazar(Cookie caja, Long pedidoId) throws Exception {
        Cookie xsrf = xsrf();
        mvc.perform(post("/api/v1/caja/pedidos/" + pedidoId + "/rechazar").cookie(caja, xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\": \"MESA_NO_OCUPADA\"}"))
                .andExpect(status().isNoContent());
    }

    private void prioridad(Cookie caja, Long pedidoId, boolean prioridadManual) throws Exception {
        Cookie xsrf = xsrf();
        mvc.perform(put("/api/v1/caja/pedidos/" + pedidoId + "/prioridad").cookie(caja, xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prioridadManual\": " + prioridadManual + "}"))
                .andExpect(status().isNoContent());
    }

    private void cocina(Cookie cocina, Long pedidoId, String accion) throws Exception {
        Cookie xsrf = xsrf();
        mvc.perform(post("/api/v1/cocina/pedidos/" + pedidoId + "/" + accion).cookie(cocina, xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isNoContent());
    }

    /** Lo que ve Carla, solo con los pedidos que armó este test y en el orden en que vienen. */
    private Map<String, List<Map<String, Object>>> aprobadosDeEstaPrueba(Cookie sesion) throws Exception {
        String respuesta = mvc.perform(get(APROBADOS).cookie(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return Map.of(
                "sinEmpezar", deEstaPrueba(JsonPath.read(respuesta, "$.sinEmpezar")),
                "enPreparacion", deEstaPrueba(JsonPath.read(respuesta, "$.enPreparacion")));
    }

    private List<Map<String, Object>> deEstaPrueba(List<Map<String, Object>> pedidos) {
        return pedidos.stream().filter(p -> ((Number) p.get("id")).longValue() > ultimoIdAntes.get("pedido")).toList();
    }

    private static List<Long> ids(List<Map<String, Object>> pedidos) {
        return pedidos.stream().map(p -> ((Number) p.get("id")).longValue()).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> pedido) {
        return (List<Map<String, Object>>) pedido.get("items");
    }

    /** El 403 por falta de permiso, y no el de la credencial provisoria (ver ControlDePermisosTest). */
    private void sinPermiso(ResultActions respuesta) throws Exception {
        respuesta.andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(SIN_PERMISO))
                .andExpect(jsonPath("$.codigo").doesNotExist());
    }

    /** Entra con el PIN como el front, con la cookie de dispositivo del usuario, como en ControlDePermisosTest. */
    private Cookie sesionDe(String nombre, String pin) throws Exception {
        Long usuarioId = idDe(nombre);
        Cookie xsrf = xsrf();
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

    /** El Admin de La Esquina entra con email y contraseña. */
    private Cookie sesionDelAdmin() throws Exception {
        Cookie xsrf = xsrf();
        String cuerpo = """
                {"email": "admin@laesquina.test", "password": "Chitgo2026!"}""";
        Cookie sesion = mvc.perform(post("/api/v1/auth/admin/login").cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
        assertThat(sesion).isNotNull();
        return sesion;
    }

    private Cookie xsrf() throws Exception {
        return mvc.perform(get("/api/v1/ping")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    // --- Ayudas: la base

    private Long idDe(String nombre) {
        return jdbc.queryForObject("""
                SELECT u.id FROM usuario u JOIN local l ON l.id = u.local_id
                WHERE u.nombre = ? AND l.nombre = 'La Esquina'""", Long.class, nombre);
    }

    private Long plato(String nombre, String local) {
        return jdbc.queryForObject("""
                SELECT p.id FROM plato p JOIN local l ON l.id = p.local_id
                WHERE p.nombre = ? AND l.nombre = ?""", Long.class, nombre, local);
    }
}
