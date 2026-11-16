package com.chitgo.pedidos.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class PantallaTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    private Long pedido(Long local, String estado) {
        Long mesa = jdbc.queryForObject("SELECT MIN(id) FROM mesa WHERE local_id=?", Long.class, local);
        jdbc.update("INSERT INTO cuenta(local_id,mesa_id,estado,abierta_en,version) VALUES(?,?,'ABIERTA',NOW(),0)", local, mesa);
        Long cuenta = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("INSERT INTO cliente_sesion(local_id,cuenta_id,token_sesion,es_iniciador) VALUES(?,?,?,true)",
                local, cuenta, UUID.randomUUID().toString());
        Long cliente = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("INSERT INTO pedido(local_id,cuenta_id,cliente_sesion_id,uuid_cliente,estado,enviado_en,version) VALUES(?,?,?,?,?,NOW(),0)",
                local, cuenta, cliente, UUID.randomUUID().toString(), estado);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Test
    void snapshotPublicoSoloIncluyePreparacionYListosDelLocalYSinDatosSensibles() throws Exception {
        Long local = jdbc.queryForObject("SELECT id FROM local WHERE nombre='La Esquina'", Long.class);
        Long otro = jdbc.queryForObject("SELECT id FROM local WHERE nombre='El Puerto'", Long.class);
        String token = jdbc.queryForObject("SELECT token_pantalla FROM local WHERE id=?", String.class, local);
        Long preparando = pedido(local, "EN_PREPARACION");
        Long listo = pedido(local, "LISTO");
        pedido(local, "SERVIDO");
        pedido(local, "APROBADO");
        pedido(otro, "LISTO");
        String ruta = "/api/v1/pantalla/" + token + "/pedidos-en-curso";
        String respuesta = mvc.perform(get(ruta)).andExpect(status().isOk())
                .andExpect(jsonPath("$.pedidos.length()").value(2)).andReturn().getResponse().getContentAsString();
        var arbol = json.readTree(respuesta);
        assertThat(arbol.propertyNames()).containsExactlyInAnyOrder("localId", "pedidos");
        assertThat(arbol.get("pedidos").get(0).propertyNames()).containsExactlyInAnyOrder("id", "mesa", "estado");
        assertThat(respuesta).doesNotContain("precio", "total", "pin", "hash", "token", "usuario", "observacion");
        jdbc.update("UPDATE pedido SET estado='SERVIDO' WHERE id=?", listo);
        mvc.perform(get(ruta)).andExpect(status().isOk()).andExpect(jsonPath("$.pedidos.length()").value(1))
                .andExpect(jsonPath("$.pedidos[0].id").value(preparando));
    }

    @Test
    void tokenDeStaffNoAbreLaPantallaYTokenDesconocidoNoRevelaElLocal() throws Exception {
        String staff = jdbc.queryForObject("SELECT token_staff FROM local WHERE nombre='La Esquina'", String.class);
        for (String token : List.of(staff, UUID.randomUUID().toString())) {
            mvc.perform(get("/api/v1/pantalla/" + token + "/pedidos-en-curso"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("La pantalla no está disponible"));
        }
    }

    @Test
    void elAdminObtieneElTokenConPermisoDeConfiguracion() throws Exception {
        Long local = jdbc.queryForObject("SELECT id FROM local WHERE nombre='La Esquina'", Long.class);
        Long usuario = jdbc.queryForObject("SELECT id FROM usuario WHERE email='admin@laesquina.test'", Long.class);
        var principal = new UsuarioAutenticado(usuario, "ADMIN", local, false, Instant.now().plusSeconds(600));
        var admin = new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("local.configurar")));
        mvc.perform(get("/api/v1/admin/pantalla").with(authentication(admin))).andExpect(status().isOk())
                .andExpect(jsonPath("$.localId").value(local)).andExpect(jsonPath("$.tokenPantalla").isString());
        mvc.perform(get("/api/v1/admin/pantalla")).andExpect(status().isUnauthorized());
    }
}
