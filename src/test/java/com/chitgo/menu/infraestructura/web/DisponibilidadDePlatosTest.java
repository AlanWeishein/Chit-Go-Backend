package com.chitgo.menu.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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

import com.chitgo.usuarios.dominio.puertos.EmisorDeHabilitaciones;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeDispositivo;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeSesion;

/**
 * HU-09 de punta a punta (CP-23-01 a CP-23-08) y CP-25-06 de HU-12. Carla y Hugo entran con el PIN de verdad, el
 * Admin con su email, y los pedidos se arman por el camino del cliente con el QR de una mesa de la seed. Contra el
 * MySQL del Compose y R__datos_de_prueba.sql, sin {@code @Transactional}, como AprobacionDePedidosTest: cada test
 * borra lo que dejó y vuelve todos los platos a activos.
 *
 * <p>Lo que viaja por el WebSocket no se mira acá: lo cubren CambiarDisponibilidadServiceTest y
 * AvisoDeDisponibilidadAdapterTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DisponibilidadDePlatosTest {

    private static final String PLATOS = "/api/v1/platos";
    private static final String TOKEN_LA_ESQUINA = "83f39b25-deac-4fde-80cc-4cfdf8117713";
    private static final String MESA_1_ESQUINA = "d4964b6b-fdae-4b4f-83e9-1c1e78a21723";
    private static final String MESA_2_ESQUINA = "7ac79c59-b48d-4db2-8365-5c6a6565d9f4";
    private static final String SIN_PERMISO = "No tenés permiso para hacer esto.";
    /** Lo que se borra al terminar, en el orden de las FK: el ticket apunta a la cuenta. */
    private static final List<String> TABLAS_DE_LA_PRUEBA = List.of(
            "registro_auditoria", "ticket", "item_pedido", "pedido", "cliente_sesion", "aviso", "cuenta");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EmisorDeHabilitaciones habilitaciones;

    private final Map<String, Long> ultimoIdAntes = new HashMap<>();

    private record Renglon(Long platoId, int cantidad) {
    }

    private record Mesa(String tokenSesion, Long cuentaId) {
    }

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
        jdbc.update("UPDATE plato SET activo = TRUE");
    }

    // --- Apagar y prender

    @Test
    void carlaApagaUnPlatoSinPedidosYSaleDelMenu() throws Exception {
        // CP-23-01
        Long milanesa = plato("Milanesa napolitana", "La Esquina");

        cambiar(sesionDe("Carla", "2468"), milanesa, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platoId").value(milanesa))
                .andExpect(jsonPath("$.disponible").value(false))
                .andExpect(jsonPath("$.afectados").isEmpty());

        assertThat(activo(milanesa)).isFalse();
        assertThat(platosDelMenu()).doesNotContain("Milanesa napolitana").contains("Ensalada mixta", "Flan casero");
        Map<String, Object> registro = unicoRegistro();
        assertThat(registro.get("accion")).isEqualTo("CAMBIAR_DISPONIBILIDAD");
        assertThat(registro.get("entidad")).isEqualTo("PLATO");
        assertThat(((Number) registro.get("entidad_id")).longValue()).isEqualTo(milanesa);
        assertThat(((Number) registro.get("usuario_id")).longValue()).isEqualTo(idDe("Carla"));
        assertThat(registro.get("antes")).isEqualTo("true");
        assertThat(registro.get("despues")).isEqualTo("false");
    }

    @Test
    void elAdminApagaUnPlatoYQuedaEnLaAuditoria() throws Exception {
        // CP-23-08: el Admin conserva el permiso como respaldo de Caja (06/10).
        Long milanesa = plato("Milanesa napolitana", "La Esquina");

        cambiar(sesionDelAdmin(), milanesa, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(false))
                .andExpect(jsonPath("$.afectados").isEmpty());

        assertThat(activo(milanesa)).isFalse();
        assertThat(platosDelMenu()).doesNotContain("Milanesa napolitana");
        Map<String, Object> registro = unicoRegistro();
        assertThat(registro.get("accion")).isEqualTo("CAMBIAR_DISPONIBILIDAD");
        assertThat(((Number) registro.get("entidad_id")).longValue()).isEqualTo(milanesa);
        assertThat(((Number) registro.get("usuario_id")).longValue()).isEqualTo(idDelAdmin());
        assertThat(registro.get("antes")).isEqualTo("true");
        assertThat(registro.get("despues")).isEqualTo("false");
    }

    @Test
    void volverlaADisponibleLaDevuelveAlMenu() throws Exception {
        // CP-23-05
        Long milanesa = plato("Milanesa napolitana", "La Esquina");
        Cookie carla = sesionDe("Carla", "2468");
        cambiar(carla, milanesa, false).andExpect(status().isOk());
        assertThat(platosDelMenu()).doesNotContain("Milanesa napolitana");

        cambiar(carla, milanesa, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(true))
                .andExpect(jsonPath("$.afectados").isEmpty());

        assertThat(activo(milanesa)).isTrue();
        assertThat(platosDelMenu()).contains("Milanesa napolitana");
        assertThat(registrosNuevos()).extracting(r -> r.get("despues")).containsExactly("false", "true");
    }

    @Test
    void apagarDosVecesDejaUnSoloRegistro() throws Exception {
        // Un doble toque en la tablet (29/10): 200 las dos veces, con los afectados de ahora, y un solo registro.
        Long milanesa = plato("Milanesa napolitana", "La Esquina");
        Long pedido = pedir(abrirMesa(MESA_1_ESQUINA), new Renglon(milanesa, 1));
        Cookie carla = sesionDe("Carla", "2468");

        for (int vez = 0; vez < 2; vez++) {
            assertThat(idsAfectados(cambiar(carla, milanesa, false)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.disponible").value(false)))).containsExactly(pedido);
        }

        assertThat(registrosNuevos()).singleElement()
                .satisfies(r -> assertThat(r).containsEntry("accion", "CAMBIAR_DISPONIBILIDAD"));
    }

    // --- Los pedidos afectados

    @Test
    void apagarDevuelveLosAfectadosConSuMesaEstadoYPlatos() throws Exception {
        // CP-23-02: Carla ve los tres antes de confirmar y los cancela con la cancelación en lote de German.
        PedidosConMilanesa armados = armarPedidosConMilanesa();
        Cookie carla = armados.carla();

        String respuesta = cambiar(carla, armados.milanesa(), false)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        List<Map<String, Object>> afectados = JsonPath.read(respuesta, "$.afectados");
        assertThat(afectados).extracting(a -> ((Number) a.get("id")).longValue())
                .containsExactly(armados.conFlan(), armados.mesa2(), armados.aprobado());
        assertThat(afectados).extracting(a -> a.get("mesa")).containsExactly("1", "2", "1");
        assertThat(afectados).extracting(a -> a.get("estado"))
                .containsExactly("PENDIENTE_APROBACION", "PENDIENTE_APROBACION", "APROBADO");
        assertThat(platosDe(afectados.get(0))).containsExactly(
                Map.of("platoId", armados.milanesa().intValue(), "nombre", "Milanesa napolitana", "cantidad", 2),
                Map.of("platoId", armados.flan().intValue(), "nombre", "Flan casero", "cantidad", 1));
        assertThat(platosDe(afectados.get(1))).extracting(p -> p.get("nombre")).containsExactly("Milanesa napolitana");
        assertThat(platosDe(afectados.get(2))).extracting(p -> p.get("nombre")).containsExactly("Milanesa napolitana");

        // Acepta cancelar: los ids van tal cual a POST /caja/pedidos/cancelar (German).
        List<Long> ids = afectados.stream().map(a -> ((Number) a.get("id")).longValue()).toList();
        enviar(post("/api/v1/caja/pedidos/cancelar").cookie(carla)
                .contentType(MediaType.APPLICATION_JSON).content("{\"pedidoIds\": " + ids + "}"))
                .andExpect(status().isNoContent());

        for (Long id : ids) {
            Map<String, Object> fila = jdbc.queryForMap("""
                    SELECT estado, cancelado_por_usuario_id, cancelado_en FROM pedido WHERE id = ?""", id);
            assertThat(fila.get("estado")).isEqualTo("CANCELADO");
            assertThat(((Number) fila.get("cancelado_por_usuario_id")).longValue()).isEqualTo(idDe("Carla"));
            assertThat(fila.get("cancelado_en")).isNotNull();
        }
        // Enteros: el Flan se fue con su pedido. Los ítems quedan como estaban.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM item_pedido WHERE pedido_id = ?", Integer.class,
                armados.conFlan())).isEqualTo(2);
        assertThat(estadoDe(armados.soloEnsalada())).isEqualTo("PENDIENTE_APROBACION");
        assertThat(idsEnLaColaDeCaja(carla)).containsExactly(armados.soloEnsalada());
        assertThat(idsEnCocina(sesionDe("Hugo", "1357"), "enCola")).doesNotContain(armados.aprobado());
        assertThat(registrosNuevos()).filteredOn(r -> "CANCELAR_PEDIDO".equals(r.get("accion")))
                .extracting(r -> ((Number) r.get("entidad_id")).longValue())
                .containsExactlyInAnyOrderElementsOf(ids);
    }

    @Test
    void apagarSinCancelarDejaLosPedidosComoEstaban() throws Exception {
        // CP-23-03: el mismo armado, y Carla dice que no en el diálogo (el front no manda nada más).
        PedidosConMilanesa armados = armarPedidosConMilanesa();
        List<Map<String, Object>> itemsAntes = itemsDeLaPrueba();

        cambiar(armados.carla(), armados.milanesa(), false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.afectados.length()").value(3));

        assertThat(activo(armados.milanesa())).isFalse();
        assertThat(estadoDe(armados.conFlan())).isEqualTo("PENDIENTE_APROBACION");
        assertThat(estadoDe(armados.mesa2())).isEqualTo("PENDIENTE_APROBACION");
        assertThat(estadoDe(armados.aprobado())).isEqualTo("APROBADO");
        assertThat(itemsDeLaPrueba()).isEqualTo(itemsAntes);
        assertThat(idsEnLaColaDeCaja(armados.carla()))
                .containsExactly(armados.conFlan(), armados.mesa2(), armados.soloEnsalada());
        assertThat(idsEnCocina(sesionDe("Hugo", "1357"), "enCola")).contains(armados.aprobado());
        assertThat(registrosNuevos()).extracting(r -> r.get("accion"))
                .containsExactly("APROBAR_PEDIDO", "CAMBIAR_DISPONIBILIDAD");
    }

    @Test
    void unPedidoEnPreparacionNoEsAfectadoYSigueEnCocina() throws Exception {
        // CP-23-04
        Long milanesa = plato("Milanesa napolitana", "La Esquina");
        Long pedido = pedir(abrirMesa(MESA_1_ESQUINA), new Renglon(milanesa, 1));
        Cookie carla = sesionDe("Carla", "2468");
        Cookie hugo = sesionDe("Hugo", "1357");
        aprobar(carla, pedido);
        cocina(hugo, pedido, "en-preparacion");

        cambiar(carla, milanesa, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.afectados").isEmpty());

        assertThat(estadoDe(pedido)).isEqualTo("EN_PREPARACION");
        assertThat(idsEnCocina(hugo, "enPreparacion")).contains(pedido);
    }

    @Test
    void losPedidosViejosYElTicketLaSiguenMostrando() throws Exception {
        // CP-23-06: la mesa 1 ya cerró con Milanesa en el ticket; la mesa 2 tiene un pedido aprobado.
        Long milanesa = plato("Milanesa napolitana", "La Esquina");
        Cookie carla = sesionDe("Carla", "2468");
        Cookie hugo = sesionDe("Hugo", "1357");
        Mesa mesa1 = abrirMesa(MESA_1_ESQUINA);
        Long servido = pedir(mesa1, new Renglon(milanesa, 2));
        aprobar(carla, servido);
        cocina(hugo, servido, "en-preparacion");
        cocina(hugo, servido, "listo");
        enviar(post("/api/v1/mozo/pedidos/" + servido + "/servido").cookie(sesionDe("Juan", "1234")))
                .andExpect(status().isNoContent());
        enviar(post("/api/v1/caja/cuentas/" + mesa1.cuentaId() + "/cerrar").cookie(carla)
                .contentType(MediaType.APPLICATION_JSON).content("{\"confirmar\": true}"))
                .andExpect(status().isOk());
        Mesa mesa2 = abrirMesa(MESA_2_ESQUINA);
        Long aprobado = pedir(mesa2, new Renglon(milanesa, 1));
        aprobar(carla, aprobado);

        cambiar(carla, milanesa, false).andExpect(status().isOk());

        mvc.perform(get("/api/v1/caja/cuentas/" + mesa1.cuentaId() + "/ticket").cookie(carla))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].plato").value("Milanesa napolitana"))
                .andExpect(jsonPath("$.items[0].cantidad").value(2));
        mvc.perform(get("/api/v1/public/sesion/estado").header("X-Session-Token", mesa2.tokenSesion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedidos[0].id").value(aprobado))
                .andExpect(jsonPath("$.pedidos[0].estado").value("APROBADO"))
                .andExpect(jsonPath("$.pedidos[0].items[0].plato").value("Milanesa napolitana"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM item_pedido WHERE pedido_id IN (?, ?)", Integer.class,
                servido, aprobado)).isEqualTo(2);
    }

    @Test
    void elAdminSacaElFlanDeLaCartaYLosPedidosViejosLoSiguenMostrando() throws Exception {
        // CP-25-06: sacar un plato de la carta es este mismo PATCH (29/10). El Flan es el destacado de la seed.
        Long flan = plato("Flan casero", "La Esquina");
        Mesa mesa = abrirMesa(MESA_1_ESQUINA);
        Long pedido = pedir(mesa, new Renglon(flan, 1));
        Cookie admin = sesionDelAdmin();

        assertThat(idsAfectados(cambiar(admin, flan, false).andExpect(status().isOk()))).containsExactly(pedido);

        String carta = cartaDelCliente();
        assertThat(JsonPath.<List<String>>read(carta, "$.categorias[*].platos[*].nombre")).doesNotContain("Flan casero");
        assertThat(JsonPath.<List<String>>read(carta, "$.destacados[*].nombre")).doesNotContain("Flan casero");
        String delAdmin = mvc.perform(get("/api/v1/admin/platos").cookie(admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(JsonPath.<List<Boolean>>read(delAdmin, "$[?(@.nombre == 'Flan casero')].disponible"))
                .containsExactly(false);
        mvc.perform(get("/api/v1/public/sesion/estado").header("X-Session-Token", mesa.tokenSesion()))
                .andExpect(jsonPath("$.pedidos[0].items[0].plato").value("Flan casero"));
        String cola = mvc.perform(get("/api/v1/caja/pedidos").cookie(sesionDe("Carla", "2468")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Map<String, Object>> items = JsonPath.read(cola, "$[?(@.id == " + pedido + ")].items[*]");
        assertThat(items).singleElement().satisfies(item -> assertThat(item)
                .containsEntry("plato", "Flan casero")
                .containsEntry("disponible", false));
        assertThat(((Number) unicoRegistro().get("usuario_id")).longValue()).isEqualTo(idDelAdmin());
    }

    // --- Quién puede y la lista de Caja

    @Test
    void cocinaNoPuedeCambiarLaDisponibilidad() throws Exception {
        // CP-23-07: Hugo manda el PATCH directo, sin pasar por ninguna pantalla.
        Long milanesa = plato("Milanesa napolitana", "La Esquina");
        Cookie hugo = sesionDe("Hugo", "1357");

        sinPermiso(cambiar(hugo, milanesa, false));
        sinPermiso(mvc.perform(get(PLATOS).cookie(hugo)));
        mvc.perform(get(PLATOS)).andExpect(status().isUnauthorized());

        assertThat(activo(milanesa)).isTrue();
        assertThat(registrosNuevos()).isEmpty();
    }

    @Test
    void unPlatoDeElPuertoDa404ComoSiNoExistiera() throws Exception {
        // RNF-10: la misma respuesta que un id que no existe.
        Long pizza = plato("Pizza muzzarella", "El Puerto");
        Cookie carla = sesionDe("Carla", "2468");

        String deOtroLocal = cambiar(carla, pizza, false)
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("El plato no existe"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String queNoExiste = cambiar(carla, 999_999L, false)
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(JsonPath.<String>read(deOtroLocal, "$.detail")).isEqualTo(JsonPath.read(queNoExiste, "$.detail"));
        assertThat(deOtroLocal).doesNotContain("Puerto");
        assertThat(activo(pizza)).isTrue();
        assertThat(registrosNuevos()).isEmpty();
    }

    @Test
    void laListaDeCajaTraeLosPlatosDeLaEsquinaConElApagado() throws Exception {
        // P-10: la seed tiene tres platos en La Esquina; la Pizza es de El Puerto.
        Cookie carla = sesionDe("Carla", "2468");
        cambiar(carla, plato("Milanesa napolitana", "La Esquina"), false).andExpect(status().isOk());

        String respuesta = mvc.perform(get(PLATOS).cookie(carla))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(JsonPath.<List<String>>read(respuesta, "$[*].nombre"))
                .containsExactly("Ensalada mixta", "Milanesa napolitana", "Flan casero");
        assertThat(JsonPath.<List<Boolean>>read(respuesta, "$[*].disponible")).containsExactly(true, false, true);
        assertThat(JsonPath.<List<String>>read(respuesta, "$[*].categoria.nombre"))
                .containsExactly("Principales", "Principales", "Postres");
    }

    @Test
    void cambiarSinElHeaderDeCsrfDa403() throws Exception {
        Long milanesa = plato("Milanesa napolitana", "La Esquina");

        mvc.perform(patch(PLATOS + "/" + milanesa + "/disponibilidad").cookie(sesionDe("Carla", "2468"), xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"disponible\": false}"))
                .andExpect(status().isForbidden());

        assertThat(activo(milanesa)).isTrue();
        assertThat(registrosNuevos()).isEmpty();
    }

    // --- Ayudas: el armado de CP-23-02 y CP-23-03

    private record PedidosConMilanesa(Cookie carla, Long milanesa, Long flan, Long conFlan, Long mesa2, Long aprobado,
                                      Long soloEnsalada) {
    }

    /**
     * Dos pedidos en espera y uno aprobado con Milanesa, uno de ellos también con Flan, y uno con solo Ensalada que
     * no tiene nada que ver. Salen en ese orden.
     */
    private PedidosConMilanesa armarPedidosConMilanesa() throws Exception {
        Long milanesa = plato("Milanesa napolitana", "La Esquina");
        Long flan = plato("Flan casero", "La Esquina");
        Long ensalada = plato("Ensalada mixta", "La Esquina");
        Mesa mesa1 = abrirMesa(MESA_1_ESQUINA);
        Mesa mesa2 = abrirMesa(MESA_2_ESQUINA);
        Cookie carla = sesionDe("Carla", "2468");

        Long conFlan = pedir(mesa1, new Renglon(milanesa, 2), new Renglon(flan, 1));
        Long deLaMesa2 = pedir(mesa2, new Renglon(milanesa, 1));
        Long aprobado = pedir(mesa1, new Renglon(milanesa, 1));
        aprobar(carla, aprobado);
        Long soloEnsalada = pedir(mesa2, new Renglon(ensalada, 1));
        return new PedidosConMilanesa(carla, milanesa, flan, conFlan, deLaMesa2, aprobado, soloEnsalada);
    }

    private static List<Long> idsAfectados(ResultActions respuesta) throws Exception {
        List<Number> ids = JsonPath.read(respuesta.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8),
                "$.afectados[*].id");
        return ids.stream().map(Number::longValue).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> platosDe(Map<String, Object> afectado) {
        return (List<Map<String, Object>>) afectado.get("platos");
    }

    // --- Ayudas: el cliente

    /** El celular escanea el QR de una mesa libre y abre la cuenta: queda como iniciador. */
    private Mesa abrirMesa(String tokenQr) throws Exception {
        String respuesta = enviar(post("/api/v1/public/mesas/" + tokenQr + "/sesiones"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.esIniciador").value(true))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String tokenSesion = JsonPath.read(respuesta, "$.tokenSesion");
        Long cuentaId = jdbc.queryForObject("SELECT cuenta_id FROM cliente_sesion WHERE token_sesion = ?", Long.class,
                tokenSesion);
        return new Mesa(tokenSesion, cuentaId);
    }

    /** Manda un pedido con la sesión del iniciador. Queda esperando a Caja. */
    private Long pedir(Mesa mesa, Renglon... renglones) throws Exception {
        String items = Arrays.stream(renglones)
                .map(r -> "{\"platoId\": %d, \"cantidad\": %d}".formatted(r.platoId(), r.cantidad()))
                .collect(Collectors.joining(", "));
        String cuerpo = "{\"uuidCliente\": \"%s\", \"items\": [%s]}".formatted(UUID.randomUUID(), items);
        String respuesta = enviar(post("/api/v1/public/pedidos").header("X-Session-Token", mesa.tokenSesion())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE_APROBACION"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
    }

    private String cartaDelCliente() throws Exception {
        return mvc.perform(get("/api/v1/public/mesas/" + MESA_1_ESQUINA + "/menu"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private List<String> platosDelMenu() throws Exception {
        return JsonPath.read(cartaDelCliente(), "$.categorias[*].platos[*].nombre");
    }

    // --- Ayudas: el personal

    /** PATCH /platos/{id}/disponibilidad con la sesión y el CSRF. */
    private ResultActions cambiar(Cookie sesion, Long platoId, boolean disponible) throws Exception {
        return enviar(patch(PLATOS + "/" + platoId + "/disponibilidad").cookie(sesion)
                .contentType(MediaType.APPLICATION_JSON).content("{\"disponible\": " + disponible + "}"));
    }

    private void aprobar(Cookie caja, Long pedidoId) throws Exception {
        enviar(post("/api/v1/caja/pedidos/" + pedidoId + "/aprobar").cookie(caja)).andExpect(status().isNoContent());
    }

    private void cocina(Cookie cocina, Long pedidoId, String accion) throws Exception {
        enviar(post("/api/v1/cocina/pedidos/" + pedidoId + "/" + accion).cookie(cocina))
                .andExpect(status().isNoContent());
    }

    /** La cola de Caja, solo con los pedidos que armó este test y en el orden en que vienen. */
    private List<Long> idsEnLaColaDeCaja(Cookie caja) throws Exception {
        String respuesta = mvc.perform(get("/api/v1/caja/pedidos").cookie(caja))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return deEstaPrueba(JsonPath.read(respuesta, "$[*].id"));
    }

    /** Una columna del KDS (enCola, enPreparacion o listos), solo con los pedidos de este test. */
    private List<Long> idsEnCocina(Cookie cocina, String columna) throws Exception {
        String respuesta = mvc.perform(get("/api/v1/cocina/pedidos").cookie(cocina))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return deEstaPrueba(JsonPath.read(respuesta, "$." + columna + "[*].id"));
    }

    private List<Long> deEstaPrueba(List<Number> ids) {
        return ids.stream().map(Number::longValue).filter(id -> id > ultimoIdAntes.get("pedido")).toList();
    }

    /** El 403 por falta de permiso, y no el de la credencial provisoria (ver ControlDePermisosTest). */
    private void sinPermiso(ResultActions respuesta) throws Exception {
        respuesta.andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(SIN_PERMISO))
                .andExpect(jsonPath("$.codigo").doesNotExist());
    }

    /** Entra con el PIN como el front, con la cookie de dispositivo del usuario, como en AprobacionDePedidosTest. */
    private Cookie sesionDe(String nombre, String pin) throws Exception {
        Long usuarioId = idDe(nombre);
        Cookie dispositivo = new Cookie(CookieDeDispositivo.NOMBRE, habilitaciones.emitir(usuarioId));
        String cuerpo = """
                {"tokenStaff": "%s", "usuarioId": %d, "pin": "%s"}""".formatted(TOKEN_LA_ESQUINA, usuarioId, pin);
        Cookie sesion = enviar(post("/api/v1/auth/pin").cookie(dispositivo)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
        assertThat(sesion).isNotNull();
        return sesion;
    }

    /** Entra con el email y la contraseña, como IngresoConEmailTest. */
    private Cookie sesionDelAdmin() throws Exception {
        Cookie sesion = enviar(post("/api/v1/auth/admin/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"admin@laesquina.test\", \"password\": \"Chitgo2026!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
        assertThat(sesion).isNotNull();
        return sesion;
    }

    /** Suma la cookie y el header de CSRF, como cualquier POST o PATCH del front. */
    private ResultActions enviar(MockHttpServletRequestBuilder pedido) throws Exception {
        Cookie xsrf = xsrf();
        return mvc.perform(pedido.cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()));
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

    private Long idDelAdmin() {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = 'admin@laesquina.test'", Long.class);
    }

    private Long plato(String nombre, String local) {
        return jdbc.queryForObject("""
                SELECT p.id FROM plato p JOIN local l ON l.id = p.local_id
                WHERE p.nombre = ? AND l.nombre = ?""", Long.class, nombre, local);
    }

    private boolean activo(Long platoId) {
        return jdbc.queryForObject("SELECT activo FROM plato WHERE id = ?", Boolean.class, platoId);
    }

    private String estadoDe(Long pedidoId) {
        return jdbc.queryForObject("SELECT estado FROM pedido WHERE id = ?", String.class, pedidoId);
    }

    private List<Map<String, Object>> itemsDeLaPrueba() {
        return jdbc.queryForList("""
                SELECT id, pedido_id, plato_id, cantidad, precio_unitario FROM item_pedido WHERE id > ? ORDER BY id""",
                ultimoIdAntes.get("item_pedido"));
    }

    /** Los registros de auditoría que dejó el test, leídos con SQL: así se ve el JSON que quedó en la columna. */
    private List<Map<String, Object>> registrosNuevos() {
        return jdbc.queryForList("""
                SELECT usuario_id, accion, entidad, entidad_id, valor_anterior ->> '$.disponible' AS antes,
                       valor_nuevo ->> '$.disponible' AS despues
                FROM registro_auditoria WHERE id > ? ORDER BY id""", ultimoIdAntes.get("registro_auditoria"));
    }

    private Map<String, Object> unicoRegistro() {
        List<Map<String, Object>> registros = registrosNuevos();
        assertThat(registros).hasSize(1);
        return registros.get(0);
    }
}
