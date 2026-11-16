package com.chitgo.usuarios.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * HU-16, tarea 3 de la ST-24: los reportes y la auditoría del Gerente/Dueño en solo lectura, de punta a punta, con el
 * login de verdad contra el MySQL del Compose y la seed. Cada test arma datos conocidos en una fecha de 2030 (La
 * Esquina y El Puerto con números distintos) y los borra; el resto lo deja como la seed {@link EmpleadosHttpBase}.
 */
class ReportesDelGerenteTest extends EmpleadosHttpBase {

    private static final String DUENO_ESQUINA = "dueno@laesquina.test";
    private static final String DUENO_PUERTO = "dueno@elpuerto.test";
    private static final Instant FECHA = Instant.parse("2030-01-15T18:00:00Z");
    private static final String PERIODO = "?desde=2030-01-15&hasta=2030-01-15";
    private static final String ACCION = "PRUEBA_ST24";
    private static final List<String> RUTAS_G = List.of("/api/v1/gerente/reportes/ventas" + PERIODO,
            "/api/v1/gerente/reportes/tiempos-preparacion" + PERIODO,
            "/api/v1/gerente/reportes/platos-mas-pedidos" + PERIODO,
            "/api/v1/gerente/auditoria?accion=" + ACCION);
    private static final List<String> RUTAS_A = List.of("/api/v1/admin/reportes/ventas" + PERIODO,
            "/api/v1/admin/reportes/tiempos-preparacion" + PERIODO,
            "/api/v1/admin/reportes/platos-mas-pedidos" + PERIODO,
            "/api/v1/admin/auditoria?accion=" + ACCION);

    private Long esquina;
    private Long puerto;
    private Long admin;

    @BeforeEach
    void datosConocidos() {
        esquina = localDe("La Esquina");
        puerto = localDe("El Puerto");
        admin = usuarioPorEmail("admin@laesquina.test");
        Long mesa = mesaDe("1", "La Esquina");
        Long mesaPuerto = mesaDe("1", "El Puerto");
        // La Esquina: 3 cierres (38500) y 3 pedidos servidos de 10, 15 y 20 minutos con 2 unidades del mismo plato.
        Long cuenta = cuenta(esquina, mesa, "23000");
        cuenta(esquina, mesa, "10000");
        cuenta(esquina, mesa, "5500");
        Long plato = jdbc.queryForObject("SELECT MIN(id) FROM plato WHERE local_id = ?", Long.class, esquina);
        for (int minutos : List.of(10, 15, 20)) {
            Long pedido = pedido(esquina, cuenta, minutos);
            jdbc.update("INSERT INTO item_pedido(local_id,pedido_id,plato_id,cantidad,precio_unitario) VALUES(?,?,?,2,100)",
                    esquina, pedido, plato);
        }
        registro(esquina, FECHA);
        registro(esquina, FECHA.plusSeconds(1));
        // El Puerto: una cuenta de 99999 y un registro, que La Esquina no tiene que ver.
        cuenta(puerto, mesaPuerto, "99999");
        registro(puerto, FECHA.plusSeconds(2));
    }

    @AfterEach
    void borrarLosItems() {
        jdbc.update("DELETE FROM item_pedido WHERE pedido_id IN (SELECT id FROM pedido WHERE enviado_en = ?)",
                Timestamp.from(FECHA.minusSeconds(3600)));
    }

    // --- CP-28-06 y el aislamiento entre locales

    @Test
    void cp_28_06_elGerenteDuenoVeLosTresReportesYLaAuditoriaDeSuLocalYNoCambiaPrecios() throws Exception {
        Cookie dueno = sesionConEmail(DUENO_ESQUINA);

        mvc.perform(get("/api/v1/gerente/reportes/ventas" + PERIODO).cookie(dueno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVentas").value(38500))
                .andExpect(jsonPath("$.cantidadCuentas").value(3));
        mvc.perform(get("/api/v1/gerente/reportes/tiempos-preparacion" + PERIODO).cookie(dueno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidadPedidos").value(3))
                .andExpect(jsonPath("$.promedioSegundos").value(900));
        mvc.perform(get("/api/v1/gerente/reportes/platos-mas-pedidos" + PERIODO).cookie(dueno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].cantidad").value(6));
        mvc.perform(get("/api/v1/gerente/auditoria?accion=" + ACCION + "&desde=2030-01-15&hasta=2030-01-15")
                        .cookie(dueno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2));

        // Cambiar el precio de la Milanesa es de /admin: el Gerente/Dueño no tiene menu.gestionar.
        Map<String, Object> milanesa = jdbc.queryForMap(
                "SELECT id, nombre, categoria_id, precio FROM plato WHERE local_id = ? AND nombre LIKE 'Milanesa%'"
                        + " ORDER BY id LIMIT 1", esquina);
        Long id = ((Number) milanesa.get("id")).longValue();
        BigDecimal antes = (BigDecimal) milanesa.get("precio");
        enviar(put("/api/v1/admin/platos/" + id).cookie(dueno).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(Map.of("nombre", milanesa.get("nombre"), "precio", 1,
                        "categoriaId", milanesa.get("categoria_id")))))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT precio FROM plato WHERE id = ?", BigDecimal.class, id))
                .isEqualByComparingTo(antes);
    }

    @Test
    void elGerenteDuenoDeElPuertoNoVeNadaDeLaEsquina() throws Exception {
        Cookie dueno = sesionConEmail(DUENO_PUERTO);

        mvc.perform(get("/api/v1/gerente/reportes/ventas" + PERIODO).cookie(dueno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVentas").value(99999))
                .andExpect(jsonPath("$.cantidadCuentas").value(1));
        mvc.perform(get("/api/v1/gerente/reportes/tiempos-preparacion" + PERIODO).cookie(dueno))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cantidadPedidos").value(0));
        mvc.perform(get("/api/v1/gerente/reportes/platos-mas-pedidos" + PERIODO).cookie(dueno))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(0));
        mvc.perform(get("/api/v1/gerente/auditoria?accion=" + ACCION).cookie(dueno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1));
        for (String ruta : RUTAS_G) {
            mvc.perform(get(ruta + (ruta.contains("?") ? "&" : "?") + "localId=" + esquina).cookie(dueno))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void elLocalIdEsOpcionalSoloSiEsElPropio() throws Exception {
        Cookie dueno = sesionConEmail(DUENO_ESQUINA);

        mvc.perform(get("/api/v1/gerente/reportes/ventas" + PERIODO + "&localId=" + esquina).cookie(dueno))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalVentas").value(38500));
        for (String ruta : RUTAS_G) {
            mvc.perform(get(ruta + (ruta.contains("?") ? "&" : "?") + "localId=" + puerto).cookie(dueno))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/v1/gerente/reportes/ventas" + PERIODO + "&localId=999999").cookie(dueno))
                .andExpect(status().isNotFound());
    }

    // --- Quién entra

    @Test
    void elAdminVeLoDeAdminYRecibe403EnLasCuatroDeGerente() throws Exception {
        Cookie adminCookie = sesionDelAdmin();

        mvc.perform(get(RUTAS_A.get(0)).cookie(adminCookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalVentas").value(38500));
        mvc.perform(get(RUTAS_A.get(3)).cookie(adminCookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(2));
        for (String ruta : RUTAS_G) {
            mvc.perform(get(ruta).cookie(adminCookie)).andExpect(status().isForbidden());
        }
    }

    @Test
    void carlaYJuanRecibenCuatroVeces403EnGerenteYEnAdmin() throws Exception {
        for (Cookie sesion : List.of(sesionDe("Carla", "2468"), sesionDe("Juan", "1234"))) {
            for (String ruta : RUTAS_G) {
                mvc.perform(get(ruta).cookie(sesion)).andExpect(status().isForbidden());
            }
            for (String ruta : RUTAS_A) {
                mvc.perform(get(ruta).cookie(sesion)).andExpect(status().isForbidden());
            }
        }
    }

    @Test
    void sinSesionDa401() throws Exception {
        for (String ruta : RUTAS_G) {
            mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void elGerenteDuenoEnAdminDocumentaLoQueHay() throws Exception {
        Cookie dueno = sesionConEmail(DUENO_ESQUINA);

        mvc.perform(get(RUTAS_A.get(0) + "&localId=" + esquina).cookie(dueno))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalVentas").value(38500));
        mvc.perform(get(RUTAS_A.get(0)).cookie(dueno)).andExpect(status().isNotFound());
        mvc.perform(get(RUTAS_A.get(0) + "&localId=" + puerto).cookie(dueno)).andExpect(status().isNotFound());
    }

    // --- Solo lectura

    @Test
    void noHayNingunaRutaQueCambieAlgoEnReportesNiAuditoria() throws Exception {
        Cookie dueno = sesionConEmail(DUENO_ESQUINA);
        List<String> rutas = List.of("/api/v1/gerente/reportes/ventas", "/api/v1/gerente/reportes/tiempos-preparacion",
                "/api/v1/gerente/reportes/platos-mas-pedidos", "/api/v1/gerente/auditoria");
        Map<String, List<Map<String, Object>>> antes = foto();
        int registrosAntes = registros();

        for (String ruta : rutas) {
            // Con sesión y CSRF, la ruta existe pero solo para GET: 405.
            enviar(post(ruta).cookie(dueno)).andExpect(status().isMethodNotAllowed());
            enviar(put(ruta).cookie(dueno)).andExpect(status().isMethodNotAllowed());
            enviar(patch(ruta).cookie(dueno)).andExpect(status().isMethodNotAllowed());
            enviar(delete(ruta).cookie(dueno)).andExpect(status().isMethodNotAllowed());
            // Sin el header de CSRF, el filtro corta antes.
            for (MockHttpServletRequestBuilder pedido : List.of(post(ruta), put(ruta), patch(ruta), delete(ruta))) {
                mvc.perform(pedido.cookie(dueno)).andExpect(status().isForbidden());
            }
        }
        enviar(post("/api/v1/gerente/reportes/otra").cookie(dueno)).andExpect(status().isNotFound());
        assertThat(foto()).isEqualTo(antes);
        assertThat(registros()).isEqualTo(registrosAntes);
    }

    @Test
    void leerReportesYAuditoriaNoEscribeNada() throws Exception {
        Cookie dueno = sesionConEmail(DUENO_ESQUINA);
        int antes = registros();
        for (String ruta : RUTAS_G) {
            mvc.perform(get(ruta).cookie(dueno)).andExpect(status().isOk());
        }
        assertThat(registros()).isEqualTo(antes);
    }

    // --- Parámetros: lo que German no prueba en /gerente

    @Test
    void lasFechasAlReveles_elTamanoYElPaginadoDanLoMismoEnGerente() throws Exception {
        Cookie dueno = sesionConEmail(DUENO_ESQUINA);

        mvc.perform(get("/api/v1/gerente/reportes/ventas?desde=2030-01-16&hasta=2030-01-15").cookie(dueno))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/gerente/auditoria?desde=2030-01-16&hasta=2030-01-15").cookie(dueno))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/gerente/reportes/ventas" + PERIODO + "&size=101").cookie(dueno))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/gerente/reportes/ventas?desde=2030-01-15").cookie(dueno))
                .andExpect(status().isBadRequest());
        // El total no depende de la página, y una página que no existe viene vacía.
        mvc.perform(get("/api/v1/gerente/reportes/ventas" + PERIODO + "&size=1&page=1").cookie(dueno))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalVentas").value(38500))
                .andExpect(jsonPath("$.detalle.contenido.length()").value(1))
                .andExpect(jsonPath("$.detalle.totalPaginas").value(3));
        mvc.perform(get("/api/v1/gerente/reportes/ventas" + PERIODO + "&size=1&page=9").cookie(dueno))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalVentas").value(38500))
                .andExpect(jsonPath("$.detalle.contenido").isEmpty());
        // Auditoría: paginado y el más nuevo primero; el usuarioId de otro local no trae nada ni da error.
        Long ultimo = jdbc.queryForObject("SELECT MAX(id) FROM registro_auditoria WHERE local_id = ? AND accion = ?",
                Long.class, esquina, ACCION);
        mvc.perform(get("/api/v1/gerente/auditoria?accion=" + ACCION + "&size=1").cookie(dueno))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido.length()").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(ultimo));
        mvc.perform(get("/api/v1/gerente/auditoria?accion=" + ACCION + "&usuarioId=" + usuarioPorEmail(DUENO_PUERTO))
                        .cookie(dueno))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido").isEmpty());
    }

    // --- Datos

    private Long cuenta(Long localId, Long mesaId, String total) {
        jdbc.update("INSERT INTO cuenta(local_id,mesa_id,estado,abierta_en,cerrada_en,total,version) "
                        + "VALUES(?,?,'CERRADA',?,?,?,0)", localId, mesaId, Timestamp.from(FECHA.minusSeconds(3600)),
                Timestamp.from(FECHA), total);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private Long pedido(Long localId, Long cuentaId, int minutos) {
        jdbc.update("INSERT INTO cliente_sesion(local_id,cuenta_id,token_sesion,es_iniciador) VALUES(?,?,?,true)",
                localId, cuentaId, UUID.randomUUID().toString());
        Long sesionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("INSERT INTO pedido(local_id,cuenta_id,cliente_sesion_id,uuid_cliente,estado,enviado_en,"
                        + "en_preparacion_en,listo_en,version) VALUES(?,?,?,?,'SERVIDO',?,?,?,0)",
                localId, cuentaId, sesionId, UUID.randomUUID().toString(), Timestamp.from(FECHA.minusSeconds(3600)),
                Timestamp.from(FECHA.minusSeconds(minutos * 60L)), Timestamp.from(FECHA));
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void registro(Long localId, Instant fecha) {
        jdbc.update("INSERT INTO registro_auditoria(local_id,usuario_id,accion,entidad,entidad_id,valor_anterior,"
                        + "valor_nuevo,creado_en) VALUES(?,?,?,'PEDIDO',5,'{\"estado\":\"APROBADO\"}',"
                        + "'{\"estado\":\"LISTO\"}',?)", localId, admin, ACCION, Timestamp.from(fecha));
    }

    private int registros() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM registro_auditoria", Integer.class);
    }

    private Long usuarioPorEmail(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
    }
}
