package com.chitgo.pedidos.infraestructura.web;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class MesasAdminTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    private static final String RUTA = "/api/v1/admin/mesas";

    private RequestPostProcessor admin() {
        Long id = jdbc.queryForObject("SELECT id FROM usuario WHERE email = 'admin@laesquina.test'", Long.class);
        Long local = jdbc.queryForObject("SELECT id FROM local WHERE nombre = 'La Esquina'", Long.class);
        return authentication(new UsernamePasswordAuthenticationToken(
                new UsuarioAutenticado(id, "ADMIN", local, false, Instant.now().plusSeconds(600)), null,
                List.of(new SimpleGrantedAuthority("mesa.gestionar"))));
    }

    private Long crear() throws Exception {
        String respuesta = mvc.perform(post(RUTA).with(admin()).with(csrf()).contentType("application/json")
                .content("{\"numero\":\"Sprint-3\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("id").asLong();
    }

    @Test
    void crearRenombrarReimprimirInhabilitarYBajaConservanHistoria() throws Exception {
        Long id = crear();
        String token = jdbc.queryForObject("SELECT token_qr FROM mesa WHERE id = ?", String.class, id);
        UUID.fromString(token);
        mvc.perform(put(RUTA + "/" + id).with(admin()).with(csrf()).contentType("application/json")
                .content("{\"numero\":\"Sprint-3-b\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tokenQr").value(token));
        byte[] qr = mvc.perform(get(RUTA + "/" + id + "/qr").with(admin()))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(qr).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47);
        mvc.perform(patch(RUTA + "/" + id + "/inhabilitada").with(admin()).with(csrf())
                .contentType("application/json").content("{\"inhabilitada\":true,\"motivo\":\"Rota\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/public/mesas/" + token + "/sesiones").with(csrf()))
                .andExpect(status().isConflict());
        mvc.perform(delete(RUTA + "/" + id).with(admin()).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(delete(RUTA + "/" + id).with(admin()).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/public/mesas/" + token)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM registro_auditoria WHERE entidad='MESA' AND entidad_id=?",
                Integer.class, id)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT activa FROM mesa WHERE id=?", Boolean.class, id)).isFalse();
    }

    @Test
    void cuentaAbiertaImpideInhabilitarOBaja() throws Exception {
        Long id = crear();
        String token = jdbc.queryForObject("SELECT token_qr FROM mesa WHERE id=?", String.class, id);
        mvc.perform(post("/api/v1/public/mesas/" + token + "/sesiones").with(csrf())).andExpect(status().isCreated());
        mvc.perform(delete(RUTA + "/" + id).with(admin()).with(csrf())).andExpect(status().isConflict());
        mvc.perform(patch(RUTA + "/" + id + "/inhabilitada").with(admin()).with(csrf())
                .contentType("application/json").content("{\"inhabilitada\":true,\"motivo\":\"Rota\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void aislamientoPermisosCsrfYValidacionDePagina() throws Exception {
        Long ajena = jdbc.queryForObject("SELECT m.id FROM mesa m JOIN local l ON l.id=m.local_id "
                + "WHERE l.nombre='El Puerto' ORDER BY m.id LIMIT 1", Long.class);
        mvc.perform(get(RUTA + "/" + ajena).with(admin())).andExpect(status().isNotFound());
        mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
        mvc.perform(get(RUTA).with(user("sinPermiso"))).andExpect(status().isForbidden());
        mvc.perform(post(RUTA).with(admin()).contentType("application/json").content("{\"numero\":\"7\"}"))
                .andExpect(status().isForbidden());
        for (String consulta : List.of("?page=-1", "?size=0", "?size=101")) {
            mvc.perform(get(RUTA + consulta).with(admin())).andExpect(status().isBadRequest());
        }
        mvc.perform(get(RUTA + "?size=2").with(admin())).andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(2)).andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2)).andExpect(jsonPath("$.totalElementos").isNumber());
    }
}
