package com.chitgo.menu.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import com.chitgo.usuarios.dominio.puertos.EmisorDeHabilitaciones;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeDispositivo;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeSesion;

/**
 * HU-12 de punta a punta (CP-25-01 a CP-25-08, menos CP-25-06, que va en la ST-12). El Admin entra con su email
 * de verdad y la carta del cliente se mira con el QR de una mesa de la seed. Contra el MySQL del Compose y
 * R__datos_de_prueba.sql, sin {@code @Transactional}, como AprobacionDePedidosTest: cada test borra lo que creó
 * y vuelve los platos, las categorías y las etiquetas de la seed a como estaban.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class CartaDelAdminTest {

    private static final String PLATOS = "/api/v1/admin/platos";
    private static final String CATEGORIAS = "/api/v1/admin/categorias";
    private static final String ETIQUETAS = "/api/v1/admin/etiquetas";
    private static final String TOKEN_LA_ESQUINA = "83f39b25-deac-4fde-80cc-4cfdf8117713";
    private static final String MESA_1_ESQUINA = "d4964b6b-fdae-4b4f-83e9-1c1e78a21723";
    private static final String SIN_PERMISO = "No tenés permiso para hacer esto.";
    /** Lo que se borra al terminar, en el orden de las FK. Los platos, categorías y etiquetas van aparte. */
    private static final List<String> TABLAS_DE_PEDIDOS =
            List.of("registro_auditoria", "item_pedido", "pedido", "cliente_sesion", "aviso", "cuenta");
    private static final List<String> TABLAS_DE_LA_CARTA = List.of("plato", "categoria_menu", "etiqueta");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private EmisorDeHabilitaciones habilitaciones;

    private final Map<String, Long> ultimoIdAntes = new HashMap<>();
    private List<Map<String, Object>> platosAntes;
    private List<Map<String, Object>> categoriasAntes;
    private List<Map<String, Object>> etiquetasAntes;
    private List<Map<String, Object>> platoEtiquetasAntes;

    @BeforeEach
    void anotarLoQueHabia() {
        for (String tabla : TABLAS_DE_PEDIDOS) {
            ultimoIdAntes.put(tabla, jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM " + tabla, Long.class));
        }
        for (String tabla : TABLAS_DE_LA_CARTA) {
            ultimoIdAntes.put(tabla, jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM " + tabla, Long.class));
        }
        platosAntes = jdbc.queryForList("""
                SELECT id, nombre, descripcion, precio, categoria_id, imagen_url, destacado, activo FROM plato""");
        categoriasAntes = jdbc.queryForList("SELECT id, nombre, orden FROM categoria_menu");
        etiquetasAntes = jdbc.queryForList("SELECT id, nombre FROM etiqueta");
        platoEtiquetasAntes = jdbc.queryForList("SELECT local_id, plato_id, etiqueta_id FROM plato_etiqueta");
    }

    /**
     * Los platos de la seed vuelven antes de borrar las categorías nuevas, por si alguno quedó en una. Las filas de
     * plato_etiqueta de los platos nuevos y de las etiquetas nuevas se van en cascada.
     */
    @AfterEach
    void dejarLaBaseComoEnLaSeed() {
        for (String tabla : TABLAS_DE_PEDIDOS) {
            jdbc.update("DELETE FROM " + tabla + " WHERE id > ?", ultimoIdAntes.get(tabla));
        }
        for (Map<String, Object> p : platosAntes) {
            jdbc.update("""
                    UPDATE plato SET nombre = ?, descripcion = ?, precio = ?, categoria_id = ?, imagen_url = ?,
                                     destacado = ?, activo = ?
                    WHERE id = ?""", p.get("nombre"), p.get("descripcion"), p.get("precio"), p.get("categoria_id"),
                    p.get("imagen_url"), p.get("destacado"), p.get("activo"), p.get("id"));
        }
        for (String tabla : TABLAS_DE_LA_CARTA) {
            jdbc.update("DELETE FROM " + tabla + " WHERE id > ?", ultimoIdAntes.get(tabla));
        }
        for (Map<String, Object> c : categoriasAntes) {
            jdbc.update("UPDATE categoria_menu SET nombre = ?, orden = ? WHERE id = ?",
                    c.get("nombre"), c.get("orden"), c.get("id"));
        }
        for (Map<String, Object> e : etiquetasAntes) {
            jdbc.update("UPDATE etiqueta SET nombre = ? WHERE id = ?", e.get("nombre"), e.get("id"));
        }
        jdbc.update("DELETE FROM plato_etiqueta");
        for (Map<String, Object> pe : platoEtiquetasAntes) {
            jdbc.update("INSERT INTO plato_etiqueta (local_id, plato_id, etiqueta_id) VALUES (?, ?, ?)",
                    pe.get("local_id"), pe.get("plato_id"), pe.get("etiqueta_id"));
        }
    }

    // --- Platos

    @Test
    void elAdminCreaUnPlatoEnUnaCategoriaNuevaYLoVeElCliente() throws Exception {
        // CP-25-01
        Cookie admin = sesionDelAdmin();
        String categoria = mandar(admin, post(CATEGORIAS), Map.of("nombre", "Entradas"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Long entradas = ((Number) JsonPath.read(categoria, "$.id")).longValue();

        String respuesta = crearPlato(admin, new Cuerpo("Empanada de carne", "Cortada a cuchillo",
                new BigDecimal("1800"), entradas, null, List.of()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Empanada de carne"))
                .andExpect(jsonPath("$.precio").value(1800.0))
                .andExpect(jsonPath("$.categoria.nombre").value("Entradas"))
                .andExpect(jsonPath("$.destacado").value(false))
                .andExpect(jsonPath("$.disponible").value(true))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Long empanada = ((Number) JsonPath.read(respuesta, "$.id")).longValue();

        // La categoría nueva va al final, y el cliente la ve con su plato.
        String carta = cartaDelCliente();
        assertThat(JsonPath.<List<String>>read(carta, "$.categorias[*].nombre"))
                .containsExactly("Principales", "Postres", "Entradas");
        assertThat(JsonPath.<List<String>>read(carta, "$.categorias[2].platos[*].nombre"))
                .containsExactly("Empanada de carne");
        assertThat(JsonPath.<Double>read(carta, "$.categorias[2].platos[0].precio")).isEqualTo(1800.0);

        List<Map<String, Object>> registros = registrosNuevos();
        assertThat(registros).extracting(r -> r.get("accion")).containsExactly("CREAR_CATEGORIA", "CREAR_PLATO");
        Map<String, Object> plato = registros.get(1);
        assertThat(plato.get("entidad")).isEqualTo("PLATO");
        assertThat(((Number) plato.get("entidad_id")).longValue()).isEqualTo(empanada);
        assertThat(((Number) plato.get("usuario_id")).longValue()).isEqualTo(idDelAdmin());
        assertThat(plato.get("nombre_nuevo")).isEqualTo("Empanada de carne");
        assertThat(new BigDecimal((String) plato.get("precio_nuevo"))).isEqualByComparingTo("1800");
        assertThat(plato.get("valor_anterior")).isNull();
    }

    @Test
    void unPlatoSinNombreSinPrecioOConPrecioNegativoNoSeGuarda() throws Exception {
        // CP-25-02
        Cookie admin = sesionDelAdmin();
        Long principales = categoria("Principales", "La Esquina");

        crearPlato(admin, new Cuerpo("", null, new BigDecimal("1800"), principales, null, List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Poné un nombre"));
        crearPlato(admin, new Cuerpo("Empanada de carne", null, null, principales, null, List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El precio tiene que ser mayor a cero"));
        crearPlato(admin, new Cuerpo("Empanada de carne", null, new BigDecimal("-100"), principales, null, List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El precio tiene que ser mayor a cero"));

        assertThat(nuevos("plato")).isZero();
        assertThat(registrosNuevos()).isEmpty();
    }

    @Test
    void subirElPrecioAuditaElAnteriorYElNuevoYNoTocaElPedidoEnEspera() throws Exception {
        // CP-25-03: el pedido sale por el camino del cliente y queda esperando a Caja.
        Long milanesa = plato("Milanesa napolitana", "La Esquina");
        Long pedido = pedir(abrirMesa(MESA_1_ESQUINA), milanesa);
        Cookie admin = sesionDelAdmin();

        mandar(admin, put(PLATOS + "/" + milanesa), new Cuerpo("Milanesa napolitana", null,
                new BigDecimal("10500"), categoria("Principales", "La Esquina"), null, List.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precio").value(10500.0));

        Map<String, Object> registro = unicoRegistro();
        assertThat(registro.get("accion")).isEqualTo("EDITAR_PLATO");
        assertThat(((Number) registro.get("entidad_id")).longValue()).isEqualTo(milanesa);
        assertThat(new BigDecimal((String) registro.get("precio_anterior"))).isEqualByComparingTo("9500");
        assertThat(new BigDecimal((String) registro.get("precio_nuevo"))).isEqualByComparingTo("10500");
        // Solo cambió el precio: es lo único que audita.
        assertThat(((Number) registro.get("claves_nuevas")).intValue()).isEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT precio_unitario FROM item_pedido WHERE pedido_id = ?",
                BigDecimal.class, pedido)).isEqualByComparingTo("9500");
        assertThat(jdbc.queryForObject("SELECT estado FROM pedido WHERE id = ?", String.class, pedido))
                .isEqualTo("PENDIENTE_APROBACION");
        assertThat(JsonPath.<List<Double>>read(cartaDelCliente(),
                "$.categorias[*].platos[?(@.nombre == 'Milanesa napolitana')].precio")).containsExactly(10500.0);
    }

    @Test
    void destacarLaEnsaladaLaPoneEnLosDestacadosDelCliente() throws Exception {
        // CP-25-04, la parte del back: que salga en los destacados del menú.
        Long ensalada = plato("Ensalada mixta", "La Esquina");
        assertThat(JsonPath.<List<String>>read(cartaDelCliente(), "$.destacados[*].nombre"))
                .containsExactly("Flan casero");

        mandar(sesionDelAdmin(), patch(PLATOS + "/" + ensalada + "/destacado"), Map.of("destacado", true))
                .andExpect(status().isNoContent());

        assertThat(JsonPath.<List<String>>read(cartaDelCliente(), "$.destacados[*].nombre"))
                .containsExactlyInAnyOrder("Ensalada mixta", "Flan casero");
        Map<String, Object> registro = unicoRegistro();
        assertThat(registro.get("accion")).isEqualTo("DESTACAR_PLATO");
        assertThat(((Number) registro.get("entidad_id")).longValue()).isEqualTo(ensalada);
        assertThat(registro.get("destacado_anterior")).isEqualTo("false");
        assertThat(registro.get("destacado_nuevo")).isEqualTo("true");
    }

    @Test
    void unNombreConSqlSeGuardaComoTexto() throws Exception {
        // CP-25-08 (RNF-15)
        String nombre = "Milanesa'; DROP TABLE plato; --";
        int platosAntesDelAlta = jdbc.queryForObject("SELECT COUNT(*) FROM plato", Integer.class);

        String respuesta = crearPlato(sesionDelAdmin(), new Cuerpo(nombre, null, new BigDecimal("9500"),
                categoria("Principales", "La Esquina"), null, List.of()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value(nombre))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Long id = ((Number) JsonPath.read(respuesta, "$.id")).longValue();

        assertThat(jdbc.queryForObject("SELECT nombre FROM plato WHERE id = ?", String.class, id)).isEqualTo(nombre);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plato", Integer.class)).isEqualTo(platosAntesDelAlta + 1);
        assertThat(JsonPath.<List<String>>read(cartaDelCliente(), "$.categorias[0].platos[*].nombre")).contains(nombre);
    }

    @Test
    void unaImagenHttpDa400YHttpsOUnaRutaDelFrontSeGuardan() throws Exception {
        Cookie admin = sesionDelAdmin();
        Long principales = categoria("Principales", "La Esquina");

        crearPlato(admin, new Cuerpo("Empanada de carne", null, new BigDecimal("1800"), principales,
                "http://fotos.example.com/empanada.jpg", List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La imagen tiene que ser un link https o una ruta del front"));
        assertThat(nuevos("plato")).isZero();

        for (String imagen : new String[] {"https://fotos.example.com/empanada.jpg", "/img/milanesa.jpg"}) {
            String respuesta = crearPlato(admin, new Cuerpo("Empanada de carne", null, new BigDecimal("1800"),
                    principales, imagen, List.of()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.imagenUrl").value(imagen))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            Long id = ((Number) JsonPath.read(respuesta, "$.id")).longValue();
            assertThat(jdbc.queryForObject("SELECT imagen_url FROM plato WHERE id = ?", String.class, id))
                    .isEqualTo(imagen);
        }
    }

    // --- Etiquetas y categorías

    @Test
    void unaEtiquetaRepetidaDa409SinImportarMayusculasNiTildes() throws Exception {
        // CP-25-05: la seed ya tiene "Sin TACC".
        Cookie admin = sesionDelAdmin();

        for (String nombre : new String[] {"sin tacc", "Sín TACC"}) {
            mandar(admin, post(ETIQUETAS), Map.of("nombre", nombre))
                    .andExpect(status().isConflict())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value("Esa etiqueta ya existe"));
        }

        assertThat(nuevos("etiqueta")).isZero();
        assertThat(registrosNuevos()).isEmpty();
    }

    @Test
    void reordenarLasCategoriasCambiaElOrdenDeLaCartaDelCliente() throws Exception {
        Long principales = categoria("Principales", "La Esquina");
        Long postres = categoria("Postres", "La Esquina");

        mandar(sesionDelAdmin(), put(CATEGORIAS + "/orden"), Map.of("ids", List.of(postres, principales)))
                .andExpect(status().isNoContent());

        assertThat(JsonPath.<List<String>>read(cartaDelCliente(), "$.categorias[*].nombre"))
                .containsExactly("Postres", "Principales");
        assertThat(unicoRegistro().get("accion")).isEqualTo("ORDENAR_CATEGORIAS");
    }

    @Test
    void borrarUnaCategoriaConPlatosDa409() throws Exception {
        Long principales = categoria("Principales", "La Esquina");

        mandar(sesionDelAdmin(), delete(CATEGORIAS + "/" + principales), null)
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("La categoría tiene 2 platos. Movelos a otra antes de borrarla"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categoria_menu WHERE id = ?", Integer.class, principales))
                .isEqualTo(1);
        assertThat(registrosNuevos()).isEmpty();
    }

    // --- Quién puede

    @Test
    void cajaNoPuedeGestionarLaCarta() throws Exception {
        // CP-25-07: Carla manda el alta directo al servicio, sin pasar por ninguna pantalla.
        Cookie carla = sesionDe("Carla", "2468");
        Long principales = categoria("Principales", "La Esquina");

        crearPlato(carla, new Cuerpo("Empanada de carne", null, new BigDecimal("1800"), principales, null, List.of()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(SIN_PERMISO));
        mvc.perform(get(PLATOS).cookie(carla))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(SIN_PERMISO));
        mvc.perform(get(PLATOS)).andExpect(status().isUnauthorized());

        assertThat(nuevos("plato")).isZero();
        assertThat(registrosNuevos()).isEmpty();
    }

    @Test
    void loDeElPuertoDa404ComoSiNoExistiera() throws Exception {
        // RNF-10: la misma respuesta que un id que no existe, también si viene en el body.
        Cookie admin = sesionDelAdmin();
        Long pizza = plato("Pizza muzzarella", "El Puerto");
        Long categoriaDelPuerto = categoria("Principales", "El Puerto");
        Long principales = categoria("Principales", "La Esquina");
        jdbc.update("INSERT INTO etiqueta (local_id, nombre) VALUES ((SELECT id FROM local WHERE nombre = 'El Puerto'), 'Picante')");
        Long etiquetaDelPuerto = jdbc.queryForObject("""
                SELECT e.id FROM etiqueta e JOIN local l ON l.id = e.local_id
                WHERE e.nombre = 'Picante' AND l.nombre = 'El Puerto'""", Long.class);
        Cuerpo pizzaCara = new Cuerpo("Pizza muzzarella", null, new BigDecimal("1"), principales, null, List.of());

        String deOtroLocal = mandar(admin, put(PLATOS + "/" + pizza), pizzaCara)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El plato no existe"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String queNoExiste = mandar(admin, put(PLATOS + "/999999"), pizzaCara)
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(JsonPath.<String>read(deOtroLocal, "$.detail")).isEqualTo(JsonPath.read(queNoExiste, "$.detail"));
        assertThat(deOtroLocal).doesNotContain("Puerto");
        mandar(admin, patch(PLATOS + "/" + pizza + "/destacado"), Map.of("destacado", true))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El plato no existe"));

        crearPlato(admin, new Cuerpo("Empanada de carne", null, new BigDecimal("1800"), categoriaDelPuerto, null,
                List.of()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("La categoría no existe"));
        crearPlato(admin, new Cuerpo("Empanada de carne", null, new BigDecimal("1800"), principales, null,
                List.of(etiquetaDelPuerto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("La etiqueta no existe"));
        mandar(admin, put(CATEGORIAS + "/" + categoriaDelPuerto), Map.of("nombre", "Pastas"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("La categoría no existe"));
        mandar(admin, delete(CATEGORIAS + "/" + categoriaDelPuerto), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("La categoría no existe"));

        assertThat(jdbc.queryForMap("SELECT precio, destacado FROM plato WHERE id = ?", pizza))
                .containsEntry("destacado", false)
                .satisfies(p -> assertThat((BigDecimal) p.get("precio")).isEqualByComparingTo("8000"));
        assertThat(jdbc.queryForObject("SELECT nombre FROM categoria_menu WHERE id = ?", String.class,
                categoriaDelPuerto)).isEqualTo("Principales");
        assertThat(nuevos("plato")).isZero();
        assertThat(registrosNuevos()).isEmpty();
    }

    @Test
    void unPostSinElHeaderDeCsrfDa403YNoCreaNada() throws Exception {
        String cuerpo = json.writeValueAsString(new Cuerpo("Empanada de carne", null, new BigDecimal("1800"),
                categoria("Principales", "La Esquina"), null, List.of()));

        mvc.perform(post(PLATOS).cookie(sesionDelAdmin(), xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isForbidden());

        assertThat(nuevos("plato")).isZero();
        assertThat(registrosNuevos()).isEmpty();
    }

    // --- Ayudas: el Admin y Caja

    /** El cuerpo de POST y PUT /admin/platos. */
    private record Cuerpo(String nombre, String descripcion, BigDecimal precio, Long categoriaId, String imagenUrl,
                          List<Long> etiquetaIds) {
    }

    private ResultActions crearPlato(Cookie sesion, Cuerpo cuerpo) throws Exception {
        return mandar(sesion, post(PLATOS), cuerpo);
    }

    /** Con la sesión, el CSRF y, si hay, el body en JSON. */
    private ResultActions mandar(Cookie sesion, MockHttpServletRequestBuilder pedido, Object cuerpo) throws Exception {
        Cookie xsrf = xsrf();
        pedido.cookie(sesion, xsrf).header("X-XSRF-TOKEN", xsrf.getValue());
        if (cuerpo != null) {
            pedido.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo));
        }
        return mvc.perform(pedido);
    }

    /** Entra con el email y la contraseña, como IngresoConEmailTest. */
    private Cookie sesionDelAdmin() throws Exception {
        Cookie xsrf = xsrf();
        Cookie sesion = mvc.perform(post("/api/v1/auth/admin/login").cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "admin@laesquina.test",
                                "password", "Chitgo2026!"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
        assertThat(sesion).isNotNull();
        return sesion;
    }

    /** Entra con el PIN como el front, con la cookie de dispositivo del usuario, como en AprobacionDePedidosTest. */
    private Cookie sesionDe(String nombre, String pin) throws Exception {
        Long usuarioId = jdbc.queryForObject("""
                SELECT u.id FROM usuario u JOIN local l ON l.id = u.local_id
                WHERE u.nombre = ? AND l.nombre = 'La Esquina'""", Long.class, nombre);
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

    private Cookie xsrf() throws Exception {
        return mvc.perform(get("/api/v1/ping")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    // --- Ayudas: el cliente

    private String cartaDelCliente() throws Exception {
        return mvc.perform(get("/api/v1/public/mesas/" + MESA_1_ESQUINA + "/menu"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** El celular escanea el QR de una mesa libre y abre la cuenta: queda como iniciador. */
    private String abrirMesa(String tokenQr) throws Exception {
        Cookie xsrf = xsrf();
        String respuesta = mvc.perform(post("/api/v1/public/mesas/" + tokenQr + "/sesiones").cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(respuesta, "$.tokenSesion");
    }

    /** Manda un pedido de un plato con la sesión del iniciador. Queda esperando a Caja. */
    private Long pedir(String tokenSesion, Long platoId) throws Exception {
        Cookie xsrf = xsrf();
        String cuerpo = """
                {"uuidCliente": "%s", "items": [{"platoId": %d, "cantidad": 1, "observacion": null}]}"""
                .formatted(UUID.randomUUID(), platoId);
        String respuesta = mvc.perform(post("/api/v1/public/pedidos").cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue()).header("X-Session-Token", tokenSesion)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE_APROBACION"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
    }

    // --- Ayudas: la base

    private Long idDelAdmin() {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = 'admin@laesquina.test'", Long.class);
    }

    private Long plato(String nombre, String local) {
        return jdbc.queryForObject("""
                SELECT p.id FROM plato p JOIN local l ON l.id = p.local_id
                WHERE p.nombre = ? AND l.nombre = ?""", Long.class, nombre, local);
    }

    private Long categoria(String nombre, String local) {
        return jdbc.queryForObject("""
                SELECT c.id FROM categoria_menu c JOIN local l ON l.id = c.local_id
                WHERE c.nombre = ? AND l.nombre = ?""", Long.class, nombre, local);
    }

    private int nuevos(String tabla) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla + " WHERE id > ?", Integer.class,
                ultimoIdAntes.get(tabla));
    }

    /** Los registros de auditoría que dejó el test, leídos con SQL: así se ve el JSON que quedó en la columna. */
    private List<Map<String, Object>> registrosNuevos() {
        return jdbc.queryForList("""
                SELECT usuario_id, accion, entidad, entidad_id, valor_anterior,
                       valor_nuevo ->> '$.nombre' AS nombre_nuevo,
                       valor_anterior ->> '$.precio' AS precio_anterior, valor_nuevo ->> '$.precio' AS precio_nuevo,
                       valor_anterior ->> '$.destacado' AS destacado_anterior,
                       valor_nuevo ->> '$.destacado' AS destacado_nuevo,
                       JSON_LENGTH(valor_nuevo) AS claves_nuevas
                FROM registro_auditoria WHERE id > ? ORDER BY id""", ultimoIdAntes.get("registro_auditoria"));
    }

    private Map<String, Object> unicoRegistro() {
        List<Map<String, Object>> registros = registrosNuevos();
        assertThat(registros).hasSize(1);
        return registros.get(0);
    }
}
