package com.chitgo.pruebas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.chitgo.usuarios.dominio.puertos.EmisorDeHabilitaciones;

/**
 * Las ayudas de los tests de integración contra el MySQL del Compose y R__datos_de_prueba.sql: el cliente por el
 * camino del celular (el QR de la mesa y el header X-Session-Token), el personal con el PIN como el front, y la
 * limpieza de lo que deja cada test. Salen de AprobacionDePedidosTest y ConcurrenciaDelClienteTest (ST-05, tarea 3),
 * que por ahora siguen con su copia.
 *
 * <p>Las cookies van por nombre, sin importar nada de usuarios/infraestructura/seguridad (regla de ST-05).
 */
public final class AyudasDeIntegracion {

    public static final String MESA_1_ESQUINA = "d4964b6b-fdae-4b4f-83e9-1c1e78a21723";
    public static final String COOKIE_DE_SESION = "chitgo_sesion";
    private static final String COOKIE_DE_DISPOSITIVO = "chitgo_dispositivo";
    private static final String TOKEN_LA_ESQUINA = "83f39b25-deac-4fde-80cc-4cfdf8117713";
    private static final String COLA_DE_CAJA = "/api/v1/caja/pedidos";
    private static final String COLA_DE_COCINA = "/api/v1/cocina/pedidos";
    /** Lo que se borra al terminar, en el orden de las FK. */
    private static final List<String> TABLAS_DE_LA_PRUEBA =
            List.of("registro_auditoria", "item_pedido", "pedido", "cliente_sesion", "aviso", "cuenta");

    private final MockMvc mvc;
    private final JdbcTemplate jdbc;
    private final EmisorDeHabilitaciones habilitaciones;
    private final Map<String, Long> ultimoIdAntes = new HashMap<>();

    public AyudasDeIntegracion(MockMvc mvc, JdbcTemplate jdbc, EmisorDeHabilitaciones habilitaciones) {
        this.mvc = mvc;
        this.jdbc = jdbc;
        this.habilitaciones = habilitaciones;
    }

    // --- La limpieza

    /** El MAX(id) de cada tabla que tocan los tests: al terminar se borra lo de arriba. */
    public void anotarLoQueHabia() {
        for (String tabla : TABLAS_DE_LA_PRUEBA) {
            ultimoIdAntes.put(tabla, jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM " + tabla, Long.class));
        }
    }

    public void borrarLoQueDejo() {
        for (String tabla : TABLAS_DE_LA_PRUEBA) {
            jdbc.update("DELETE FROM " + tabla + " WHERE id > ?", ultimoIdAntes.get(tabla));
        }
    }

    public long ultimoIdAntes(String tabla) {
        return ultimoIdAntes.get(tabla);
    }

    // --- El cliente

    /** Un celular nuevo escanea el QR: sin X-Session-Token. */
    public ResultActions escanear(String tokenQr) throws Exception {
        return mvc.perform(postConCsrf("/api/v1/public/mesas/" + tokenQr + "/sesiones"));
    }

    /** El celular escanea el QR de una mesa libre y abre la cuenta: queda como iniciador. */
    public String abrirMesa(String tokenQr) throws Exception {
        String respuesta = escanear(tokenQr)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.esIniciador").value(true))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(respuesta, "$.tokenSesion");
    }

    public ResultActions enviar(String tokenSesion, String cuerpo) throws Exception {
        return mvc.perform(postConCsrf("/api/v1/public/pedidos").header("X-Session-Token", tokenSesion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    /** Manda un pedido de un plato con la sesión del iniciador. Sale con la hora del reloj de prueba. */
    public Long pedir(String tokenSesion, Long platoId, int cantidad, String observacion) throws Exception {
        String observacionJson = observacion == null ? "null" : "\"" + observacion + "\"";
        String cuerpo = """
                {"uuidCliente": "%s", "items": [{"platoId": %d, "cantidad": %d, "observacion": %s}]}"""
                .formatted(UUID.randomUUID(), platoId, cantidad, observacionJson);
        String respuesta = enviar(tokenSesion, cuerpo)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE_APROBACION"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
    }

    // --- El personal

    /** Entra con el PIN como el front, con la cookie de dispositivo del usuario, como en ControlDePermisosTest. */
    public Cookie sesionDe(String nombre, String pin) throws Exception {
        Long usuarioId = idDe(nombre);
        Cookie dispositivo = new Cookie(COOKIE_DE_DISPOSITIVO, habilitaciones.emitir(usuarioId));
        String cuerpo = """
                {"tokenStaff": "%s", "usuarioId": %d, "pin": "%s"}""".formatted(TOKEN_LA_ESQUINA, usuarioId, pin);
        Cookie sesion = mvc.perform(postConCsrf("/api/v1/auth/pin").cookie(dispositivo)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(COOKIE_DE_SESION);
        assertThat(sesion).isNotNull();
        return sesion;
    }

    public ResultActions aprobar(Cookie sesion, Long pedidoId) throws Exception {
        return mvc.perform(postConCsrf(COLA_DE_CAJA + "/" + pedidoId + "/aprobar").cookie(sesion));
    }

    public ResultActions rechazar(Cookie sesion, Long pedidoId, String cuerpo) throws Exception {
        return mvc.perform(postConCsrf(COLA_DE_CAJA + "/" + pedidoId + "/rechazar").cookie(sesion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    public ResultActions enPreparacion(Cookie sesion, Long pedidoId) throws Exception {
        return mvc.perform(postConCsrf(COLA_DE_COCINA + "/" + pedidoId + "/en-preparacion").cookie(sesion));
    }

    public ResultActions listo(Cookie sesion, Long pedidoId) throws Exception {
        return mvc.perform(postConCsrf(COLA_DE_COCINA + "/" + pedidoId + "/listo").cookie(sesion));
    }

    public Cookie xsrf() throws Exception {
        return mvc.perform(get("/api/v1/ping")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    /** Un POST como lo manda el front: la cookie XSRF-TOKEN y el mismo valor en el header X-XSRF-TOKEN. */
    private MockHttpServletRequestBuilder postConCsrf(String url) throws Exception {
        Cookie xsrf = xsrf();
        return post(url).cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue());
    }

    // --- La base

    /** Un usuario de La Esquina, por su nombre. */
    public Long idDe(String nombre) {
        return jdbc.queryForObject("""
                SELECT u.id FROM usuario u JOIN local l ON l.id = u.local_id
                WHERE u.nombre = ? AND l.nombre = 'La Esquina'""", Long.class, nombre);
    }

    public Long localDe(String nombre) {
        return jdbc.queryForObject("SELECT id FROM local WHERE nombre = ?", Long.class, nombre);
    }

    public Long plato(String nombre, String local) {
        return jdbc.queryForObject("""
                SELECT p.id FROM plato p JOIN local l ON l.id = p.local_id
                WHERE p.nombre = ? AND l.nombre = ?""", Long.class, nombre, local);
    }

    public Long mesaDe(String tokenQr) {
        return jdbc.queryForObject("SELECT id FROM mesa WHERE token_qr = ?", Long.class, tokenQr);
    }

    public int registrosNuevos() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM registro_auditoria WHERE id > ?", Integer.class,
                ultimoIdAntes("registro_auditoria"));
    }
}
