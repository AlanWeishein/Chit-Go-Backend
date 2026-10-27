package com.chitgo.pedidos.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.chitgo.pruebas.AyudasDeIntegracion;
import com.chitgo.usuarios.dominio.puertos.EmisorDeHabilitaciones;

/**
 * CP-SEG-01 en los endpoints del Sprint 1 (ST-05, tarea 4): cada uno llamado con un rol que no corresponde da 403, y
 * sin sesión, 401. Solo lo que no cubrían ControlDePermisosTest (Mozo y Cocina en la cola de Caja, Mozo en las tres
 * de cocina, la cola de Caja sin sesión) y AprobacionDePedidosTest (Mozo en aprobar y rechazar).
 *
 * <p>Los POST van con un id que no existe, como en ControlDePermisosTest: el 403 tiene que salir antes de buscar el
 * pedido, y si el permiso no se mirara la respuesta sería un 404, que es lo que recibe el rol que sí corresponde.
 * Contra el MySQL del Compose y los usuarios de R__datos_de_prueba.sql, sin {@code @Transactional}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PermisosDelSprint1Test {

    private static final String SIN_PERMISO = "No tenés permiso para hacer esto.";
    private static final String CONTRASENA = "Chitgo2026!";

    /** Las seis rutas del personal: tres piden pedido.aprobar_rechazar y tres, pedido.cambiar_estado_cocina. */
    enum Ruta {
        COLA_DE_CAJA("/api/v1/caja/pedidos", false),
        APROBAR("/api/v1/caja/pedidos/999999/aprobar", true),
        RECHAZAR("/api/v1/caja/pedidos/999999/rechazar", true),
        COLA_DE_COCINA("/api/v1/cocina/pedidos", false),
        EN_PREPARACION("/api/v1/cocina/pedidos/999999/en-preparacion", true),
        LISTO("/api/v1/cocina/pedidos/999999/listo", true);

        final String url;
        final boolean esPost;

        Ruta(String url, boolean esPost) {
            this.url = url;
            this.esPost = esPost;
        }
    }

    /** Las tres rutas del cliente, que se identifica con el header X-Session-Token. */
    enum RutaDelCliente {
        ENVIAR_PEDIDO,
        CANCELAR_PEDIDO,
        ESTADO_DE_LA_SESION
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EmisorDeHabilitaciones habilitaciones;

    private AyudasDeIntegracion ayudas;

    @BeforeEach
    void anotarLoQueHabia() {
        ayudas = new AyudasDeIntegracion(mvc, jdbc, habilitaciones);
        ayudas.anotarLoQueHabia();
    }

    @AfterEach
    void dejarLaBaseComoEnLaSeed() {
        ayudas.borrarLoQueDejo();
    }

    /** Cocina en aprobar y rechazar, Caja en las de cocina, y el Admin y el Gerente/Dueño en las seis. */
    static Stream<Arguments> rolesQueNoCorresponden() {
        Stream<Arguments> cocinaYCaja = Stream.of(
                arguments("Cocina", Ruta.APROBAR),
                arguments("Cocina", Ruta.RECHAZAR),
                arguments("Caja", Ruta.COLA_DE_COCINA),
                arguments("Caja", Ruta.EN_PREPARACION),
                arguments("Caja", Ruta.LISTO));
        Stream<Arguments> sinLocal = Stream.of("Admin", "Gerente/Dueño")
                .flatMap(rol -> Arrays.stream(Ruta.values()).map(ruta -> arguments(rol, ruta)));
        return Stream.concat(cocinaYCaja, sinLocal);
    }

    @ParameterizedTest(name = "{0} en {1}")
    @MethodSource("rolesQueNoCorresponden")
    void unRolQueNoCorrespondeRecibe403(String rol, Ruta ruta) throws Exception {
        ResultActions respuesta = mvc.perform(pedidoA(ruta).cookie(sesionDe(rol)));

        // El 403 por falta de permiso, y no el de la credencial provisoria, que trae codigo.
        respuesta.andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(SIN_PERMISO))
                .andExpect(jsonPath("$.codigo").doesNotExist());
    }

    @ParameterizedTest
    @EnumSource(Ruta.class)
    void conElRolQueCorrespondeElMismoPedidoPasaElPermiso(Ruta ruta) throws Exception {
        // El control de los 403 de arriba: el CSRF que falta también da 403 con el mismo detail, así que sin esto un
        // token mal armado haría pasar el test sin mirar el permiso. Los POST llegan a buscar el pedido: 404.
        String rol = ruta.url.contains("/caja/") ? "Caja" : "Cocina";
        ResultActions respuesta = mvc.perform(pedidoA(ruta).cookie(sesionDe(rol)));

        if (ruta.esPost) {
            respuesta.andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("El pedido no existe"));
        } else {
            respuesta.andExpect(status().isOk());
        }
    }

    @ParameterizedTest
    @EnumSource(value = Ruta.class, names = "COLA_DE_CAJA", mode = EnumSource.Mode.EXCLUDE)
    void sinSesionDa401(Ruta ruta) throws Exception {
        // Con el token CSRF bien puesto, para que el que responda sea el control de la sesión.
        mvc.perform(pedidoA(ruta))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("No hay una sesión iniciada, o venció."));
    }

    @ParameterizedTest
    @EnumSource(RutaDelCliente.class)
    void elClienteSinTokenDeSesionRecibe403(RutaDelCliente ruta) throws Exception {
        // La sección 1 del contrato: si el token de sesión no corresponde, 403. CP-SEG-01 dice "401 sin sesión",
        // pero el cliente no tiene sesión del personal.
        mvc.perform(pedidoDelClienteA(ruta))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("La sesión no es válida"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pedido WHERE id > ?", Integer.class,
                ayudas.ultimoIdAntes("pedido"))).isZero();
    }

    private MockHttpServletRequestBuilder pedidoA(Ruta ruta) throws Exception {
        if (!ruta.esPost) {
            return get(ruta.url);
        }
        MockHttpServletRequestBuilder pedido = conCsrf(post(ruta.url));
        // Rechazar valida el cuerpo antes de llegar al permiso: va uno válido.
        return ruta == Ruta.RECHAZAR
                ? pedido.contentType(MediaType.APPLICATION_JSON).content("{\"motivo\": \"MESA_NO_OCUPADA\"}")
                : pedido;
    }

    private MockHttpServletRequestBuilder pedidoDelClienteA(RutaDelCliente ruta) throws Exception {
        return switch (ruta) {
            case ENVIAR_PEDIDO -> conCsrf(post("/api/v1/public/pedidos"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"uuidCliente": "%s", "items": [{"platoId": %d, "cantidad": 1, "observacion": null}]}"""
                            .formatted(UUID.randomUUID(), ayudas.plato("Milanesa napolitana", "La Esquina")));
            case CANCELAR_PEDIDO -> conCsrf(post("/api/v1/public/pedidos/999999/cancelar"));
            case ESTADO_DE_LA_SESION -> get("/api/v1/public/sesion/estado");
        };
    }

    private Cookie sesionDe(String rol) throws Exception {
        return switch (rol) {
            case "Caja" -> ayudas.sesionDe("Carla", "2468");
            case "Cocina" -> ayudas.sesionDe("Hugo", "1357");
            case "Admin" -> conEmail("admin@laesquina.test");
            case "Gerente/Dueño" -> conEmail("dueno@laesquina.test");
            default -> throw new IllegalArgumentException(rol);
        };
    }

    /** El login del Admin y del Gerente/Dueño, como en IngresoConEmailTest. */
    private Cookie conEmail(String email) throws Exception {
        String cuerpo = """
                {"email": "%s", "password": "%s"}""".formatted(email, CONTRASENA);
        Cookie sesion = mvc.perform(conCsrf(post("/api/v1/auth/admin/login"))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(AyudasDeIntegracion.COOKIE_DE_SESION);
        assertThat(sesion).isNotNull();
        return sesion;
    }

    private MockHttpServletRequestBuilder conCsrf(MockHttpServletRequestBuilder pedido) throws Exception {
        Cookie xsrf = ayudas.xsrf();
        return pedido.cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue());
    }
}
