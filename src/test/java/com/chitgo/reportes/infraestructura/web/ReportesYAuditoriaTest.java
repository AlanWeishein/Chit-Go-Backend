package com.chitgo.reportes.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
import com.chitgo.auditoria.infraestructura.programacion.RetencionDeAuditoria;
import com.chitgo.compartido.aplicacion.ReglasDePrueba;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class ReportesYAuditoriaTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    Long local, otroLocal, usuario, propietario, mesa, otraMesa;
    private static final Instant FECHA = Instant.parse("2030-01-15T18:00:00Z");
    private static final String PERIODO = "?desde=2030-01-15&hasta=2030-01-15";

    @BeforeEach
    void datos() {
        local = jdbc.queryForObject("SELECT id FROM local WHERE nombre='La Esquina'", Long.class);
        otroLocal = jdbc.queryForObject("SELECT id FROM local WHERE nombre='El Puerto'", Long.class);
        usuario = jdbc.queryForObject("SELECT id FROM usuario WHERE email='admin@laesquina.test'", Long.class);
        propietario = jdbc.queryForObject("SELECT propietario_usuario_id FROM local WHERE id=?", Long.class, local);
        mesa = jdbc.queryForObject("SELECT MIN(id) FROM mesa WHERE local_id=?", Long.class, local);
        otraMesa = jdbc.queryForObject("SELECT MIN(id) FROM mesa WHERE local_id=?", Long.class, otroLocal);
    }

    private RequestPostProcessor admin(String permiso) { return sesion(usuario, "ADMIN", local, permiso); }
    private RequestPostProcessor dueno(String permiso) { return sesion(propietario, "GERENTE_DUENO", null, permiso); }
    private RequestPostProcessor sesion(Long id, String rol, Long localId, String permiso) {
        return authentication(new UsernamePasswordAuthenticationToken(
                new UsuarioAutenticado(id, rol, localId, false, Instant.now().plusSeconds(600)), null,
                List.of(new SimpleGrantedAuthority(permiso))));
    }

    private Long cuenta(Long localId, Long mesaId, String estado, String total, Instant fecha) {
        jdbc.update("INSERT INTO cuenta(local_id,mesa_id,estado,abierta_en,cerrada_en,total,version) VALUES(?,?,?, ?,?,?,0)",
                localId, mesaId, estado, Timestamp.from(fecha.minusSeconds(3600)),
                "CERRADA".equals(estado) ? Timestamp.from(fecha) : null, total);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private Long pedido(Long cuentaId, String estado, int minutos) {
        jdbc.update("INSERT INTO cliente_sesion(local_id,cuenta_id,token_sesion,es_iniciador) VALUES(?,?,?,true)",
                local, cuentaId, UUID.randomUUID().toString());
        Long sesionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("INSERT INTO pedido(local_id,cuenta_id,cliente_sesion_id,uuid_cliente,estado,enviado_en,en_preparacion_en,listo_en,version) "
                + "VALUES(?,?,?,?,?,?,?,?,0)", local, cuentaId, sesionId, UUID.randomUUID().toString(), estado,
                Timestamp.from(FECHA.minusSeconds(3600)), Timestamp.from(FECHA.minusSeconds(minutos * 60L)), Timestamp.from(FECHA));
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Test
    void ventasSumanSoloCierresDelPeriodoYTotalNoDependeDeLaPagina() throws Exception {
        cuenta(local, mesa, "CERRADA", "23000", FECHA);
        cuenta(local, mesa, "CERRADA", "10000", FECHA.plusSeconds(5));
        cuenta(local, mesa, "CERRADA", "5500", FECHA.plusSeconds(10));
        cuenta(local, mesa, "ABIERTA", "99999", FECHA);
        cuenta(otroLocal, otraMesa, "CERRADA", "99999", FECHA);
        // A las 02:59:59 UTC todavía es el día anterior en Argentina.
        cuenta(local, mesa, "CERRADA", "99999", Instant.parse("2030-01-15T02:59:59Z"));
        mvc.perform(get("/api/v1/admin/reportes/ventas" + PERIODO + "&size=1&page=1").with(admin("reporte.ver")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalVentas").value(38500))
                .andExpect(jsonPath("$.cantidadCuentas").value(3)).andExpect(jsonPath("$.detalle.contenido.length()").value(1))
                .andExpect(jsonPath("$.detalle.totalPaginas").value(3));
    }

    @Test
    void tiemposDeDiezQuinceYVeinteMinutosPromedianQuinceYRankingSumaCantidades() throws Exception {
        Long cuenta = cuenta(local, mesa, "CERRADA", "38500", FECHA);
        Long plato = jdbc.queryForObject("SELECT MIN(id) FROM plato WHERE local_id=?", Long.class, local);
        for (int minutos : List.of(10, 15, 20)) {
            Long pedido = pedido(cuenta, "SERVIDO", minutos);
            jdbc.update("INSERT INTO item_pedido(local_id,pedido_id,plato_id,cantidad,precio_unitario) VALUES(?,?,?,2,100)",
                    local, pedido, plato);
        }
        Long rechazado = pedido(cuenta, "RECHAZADO", 10);
        jdbc.update("UPDATE pedido SET en_preparacion_en=NULL,listo_en=NULL WHERE id=?", rechazado);
        jdbc.update("INSERT INTO item_pedido(local_id,pedido_id,plato_id,cantidad,precio_unitario) VALUES(?,?,?,20,100)",
                local, rechazado, plato);
        mvc.perform(get("/api/v1/admin/reportes/tiempos-preparacion" + PERIODO + "&size=1").with(admin("reporte.ver")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cantidadPedidos").value(3))
                .andExpect(jsonPath("$.promedioSegundos").value(900));
        mvc.perform(get("/api/v1/admin/reportes/platos-mas-pedidos" + PERIODO).with(admin("reporte.ver")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido[0].cantidad").value(6))
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    void duenoSoloConsultaSuLocalYSeValidanFechasPaginasYPermisos() throws Exception {
        mvc.perform(get("/api/v1/gerente/reportes/ventas" + PERIODO + "&localId=" + local).with(dueno("reporte.ver")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalVentas").value(0))
                .andExpect(jsonPath("$.detalle.contenido").isEmpty());
        mvc.perform(get("/api/v1/gerente/reportes/ventas" + PERIODO + "&localId=" + otroLocal).with(dueno("reporte.ver")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/reportes/ventas" + PERIODO + "&localId=" + otroLocal).with(admin("reporte.ver")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/reportes/ventas?desde=2030-01-16&hasta=2030-01-15").with(admin("reporte.ver")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/reportes/ventas" + PERIODO + "&size=101").with(admin("reporte.ver")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/reportes/ventas" + PERIODO)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/reportes/ventas" + PERIODO).with(admin("mesa.gestionar")))
                .andExpect(status().isForbidden());
    }

    private Long registro(Long localId, String accion, Instant fecha) {
        jdbc.update("INSERT INTO registro_auditoria(local_id,usuario_id,accion,entidad,entidad_id,valor_anterior,valor_nuevo,creado_en) "
                + "VALUES(?,?,?,'PEDIDO',5,'{\"estado\":\"APROBADO\"}','{\"estado\":\"LISTO\"}',?)",
                localId, usuario, accion, Timestamp.from(fecha));
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Test
    void auditoriaFiltraOrdenaPaginaYDevuelveJsonSinHabilitarEscrituras() throws Exception {
        registro(local, "APROBAR_PEDIDO", FECHA);
        Long ultimo = registro(local, "APROBAR_PEDIDO", FECHA.plusSeconds(1));
        registro(otroLocal, "APROBAR_PEDIDO", FECHA.plusSeconds(2));
        registro(local, "SERVIR_PEDIDO", FECHA.plusSeconds(3));
        mvc.perform(get("/api/v1/admin/auditoria" + PERIODO + "&entidad=PEDIDO&accion=APROBAR_PEDIDO&usuarioId=" + usuario + "&size=1")
                .with(admin("auditoria.ver")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[0].id").value(ultimo))
                .andExpect(jsonPath("$.contenido[0].valorAnterior.estado").value("APROBADO"))
                .andExpect(jsonPath("$.contenido[0].valorNuevo.estado").value("LISTO"));
        mvc.perform(get("/api/v1/admin/auditoria?accion=' OR 1=1 --").with(admin("auditoria.ver")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido").isEmpty());
        mvc.perform(get("/api/v1/gerente/auditoria?localId=" + otroLocal).with(dueno("auditoria.ver")))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/admin/auditoria").with(admin("auditoria.ver")).with(csrf()))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void retencionRespetaElLimiteExactoDeSeisMeses() {
        Instant ahora = Instant.parse("2026-11-16T18:00:00Z");
        Instant corte = Instant.parse("2026-05-16T18:00:00Z");
        Long vencido = registro(local, "VENCIDO", corte.minusSeconds(1));
        Long vigente = registro(local, "VIGENTE", corte);
        new RetencionDeAuditoria(jdbc, () -> ahora, ReglasDePrueba.DE_HOY).eliminarVencidos();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM registro_auditoria WHERE id=?", Integer.class, vencido)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM registro_auditoria WHERE id=?", Integer.class, vigente)).isEqualTo(1);
    }
}
