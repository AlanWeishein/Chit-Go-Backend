package com.chitgo.pedidos.infraestructura.web;

import static com.chitgo.pruebas.CsrfReal.csrfReal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.chitgo.compartido.aplicacion.ReglasDelNegocio;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

/** Integración con HU-02/03/05/07/10/11/14: cada request confirma su propia transacción. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class FlujoSprint3Test {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired ReglasDelNegocio reglas;
    private Long mesaCreada;
    private long auditoriaAntes;

    @BeforeEach
    void antes() { auditoriaAntes = jdbc.queryForObject("SELECT COALESCE(MAX(id),0) FROM registro_auditoria", Long.class); }

    @AfterEach
    void limpiar() {
        if (mesaCreada == null) return;
        jdbc.update("DELETE FROM registro_auditoria WHERE local_id=? AND id>?", local(), auditoriaAntes);
        jdbc.update("DELETE FROM ticket WHERE cuenta_id IN (SELECT id FROM cuenta WHERE mesa_id=?)", mesaCreada);
        jdbc.update("DELETE FROM item_pedido WHERE pedido_id IN (SELECT p.id FROM pedido p JOIN cuenta c ON c.id=p.cuenta_id WHERE c.mesa_id=?)", mesaCreada);
        jdbc.update("DELETE FROM pedido WHERE cuenta_id IN (SELECT id FROM cuenta WHERE mesa_id=?)", mesaCreada);
        jdbc.update("DELETE FROM cliente_sesion WHERE cuenta_id IN (SELECT id FROM cuenta WHERE mesa_id=?)", mesaCreada);
        jdbc.update("DELETE FROM aviso WHERE mesa_id=?", mesaCreada);
        jdbc.update("DELETE FROM cuenta WHERE mesa_id=?", mesaCreada);
        jdbc.update("DELETE FROM mesa WHERE id=?", mesaCreada);
    }

    private Long local() { return jdbc.queryForObject("SELECT id FROM local WHERE nombre='La Esquina'", Long.class); }
    private Long usuario(String rol) {
        return jdbc.queryForObject("SELECT u.id FROM usuario u JOIN rol r ON r.id=u.rol_id WHERE u.local_id=? "
                + "AND r.codigo=? ORDER BY u.id LIMIT 1", Long.class, local(), rol);
    }
    private RequestPostProcessor personal(String rol) {
        var permisos = jdbc.queryForList("SELECT p.codigo FROM permiso p JOIN rol_permiso rp ON rp.permiso_id=p.id "
                + "JOIN rol r ON r.id=rp.rol_id WHERE r.codigo=?", String.class, rol).stream().map(SimpleGrantedAuthority::new).toList();
        return authentication(new UsernamePasswordAuthenticationToken(
                new UsuarioAutenticado(usuario(rol), rol, local(), false, Instant.now().plusSeconds(3600)), null, permisos));
    }
    private JsonNode respuesta(String texto) { return json.readTree(texto); }

    private JsonNode fila(JsonNode lista, String campo, long id) {
        for (JsonNode fila : lista) if (fila.get(campo).asLong() == id) return fila;
        return null;
    }

    @Test
    void mesaNuevaSeAsignaRecibePedidoSeSirveSeCobraYApareceEnReportesYAuditoria() throws Exception {
        JsonNode mesa = respuesta(mvc.perform(post("/api/v1/admin/mesas").with(personal("ADMIN")).with(csrfReal(mvc))
                .contentType("application/json").content("{\"numero\":\"Integracion S3\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        Long mesaId = mesa.get("id").asLong();
        mesaCreada = mesaId;
        Long empleado = jdbc.queryForObject("SELECT id FROM empleado WHERE usuario_id=?", Long.class, usuario("MOZO"));
        var asignadas = new ArrayList<>(jdbc.queryForList("SELECT mesa_id FROM mozo_mesa WHERE empleado_id=?", Long.class, empleado));
        asignadas.add(mesaId);
        mvc.perform(put("/api/v1/admin/empleados/" + empleado + "/mesas").with(personal("ADMIN")).with(csrfReal(mvc))
                .contentType("application/json").content(json.writeValueAsString(Map.of("mesaIds", asignadas))))
                .andExpect(status().isOk());
        JsonNode sesion = respuesta(mvc.perform(post("/api/v1/public/mesas/" + mesa.get("tokenQr").asText() + "/sesiones").with(csrfReal(mvc)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String cliente = sesion.get("tokenSesion").asText();
        Long plato = jdbc.queryForObject("SELECT MIN(id) FROM plato WHERE local_id=? AND activo=true", Long.class, local());
        JsonNode pedido = respuesta(mvc.perform(post("/api/v1/public/pedidos").with(csrfReal(mvc)).header("X-Session-Token", cliente)
                .contentType("application/json").content(json.writeValueAsString(Map.of("uuidCliente", UUID.randomUUID().toString(),
                        "items", List.of(Map.of("platoId", plato, "cantidad", 2))))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        Long pedidoId = pedido.get("id").asLong();
        mvc.perform(post("/api/v1/caja/pedidos/" + pedidoId + "/aprobar").with(personal("CAJA")).with(csrfReal(mvc)))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/cocina/pedidos/" + pedidoId + "/en-preparacion").with(personal("COCINA")).with(csrfReal(mvc)))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/cocina/pedidos/" + pedidoId + "/listo").with(personal("COCINA")).with(csrfReal(mvc)))
                .andExpect(status().isNoContent());
        String tokenPantalla = respuesta(mvc.perform(get("/api/v1/admin/pantalla").with(personal("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("tokenPantalla").asText();
        String pantalla = "/api/v1/pantalla/" + tokenPantalla + "/pedidos-en-curso";
        var visible = respuesta(mvc.perform(get(pantalla)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("pedidos");
        assertThat(fila(visible, "id", pedidoId).get("estado").asText()).isEqualTo("LISTO");
        mvc.perform(post("/api/v1/mozo/pedidos/" + pedidoId + "/servido").with(personal("MOZO")).with(csrfReal(mvc)))
                .andExpect(status().isNoContent());
        var servidos = respuesta(mvc.perform(get(pantalla)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("pedidos");
        assertThat(fila(servidos, "id", pedidoId)).isNull();
        mvc.perform(post("/api/v1/public/cuenta/solicitar").with(csrfReal(mvc)).header("X-Session-Token", cliente))
                .andExpect(status().isNoContent());
        Long cuenta = jdbc.queryForObject("SELECT cuenta_id FROM pedido WHERE id=?", Long.class, pedidoId);
        mvc.perform(post("/api/v1/caja/cuentas/" + cuenta + "/cerrar").with(personal("CAJA")).with(csrfReal(mvc)))
                .andExpect(status().isOk());
        String fecha = LocalDate.now(reglas.zonaHoraria()).toString();
        String periodo = "?desde=" + fecha + "&hasta=" + fecha + "&size=100";
        var ventas = respuesta(mvc.perform(get("/api/v1/admin/reportes/ventas" + periodo).with(personal("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("detalle").get("contenido");
        assertThat(fila(ventas, "cuentaId", cuenta)).isNotNull();
        var auditoria = respuesta(mvc.perform(get("/api/v1/admin/auditoria" + periodo + "&entidad=CUENTA&accion=CERRAR_CUENTA")
                .with(personal("ADMIN"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("contenido");
        assertThat(fila(auditoria, "entidadId", cuenta)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT estado FROM cuenta WHERE id=?", String.class, cuenta)).isEqualTo("CERRADA");
        mvc.perform(delete("/api/v1/admin/mesas/" + mesaId).with(personal("ADMIN")).with(csrfReal(mvc)))
                .andExpect(status().isNoContent());
    }
}
