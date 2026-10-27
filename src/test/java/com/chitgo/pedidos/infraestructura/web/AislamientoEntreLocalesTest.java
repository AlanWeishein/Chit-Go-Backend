package com.chitgo.pedidos.infraestructura.web;

import static com.chitgo.pruebas.AyudasDeIntegracion.MESA_1_ESQUINA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.chitgo.compartido.dominio.RelojDePrueba;
import com.chitgo.pedidos.dominio.Pedido;
import com.chitgo.pedidos.dominio.puertos.PedidoRepository;
import com.chitgo.pruebas.AyudasDeIntegracion;
import com.chitgo.usuarios.dominio.puertos.EmisorDeHabilitaciones;

/**
 * CP-RNF10-01 (ST-05, tarea 4): con pedidos en los dos locales, los endpoints del Sprint 1 usados desde La Esquina
 * con ids de El Puerto no devuelven ni cambian nada de El Puerto, y responden 404 como si el id no existiera.
 * Aprobar y rechazar ya están en CP-19-08 (AprobacionDePedidosTest), el login con PIN en IngresoConPinTest y la
 * suscripción a un topic de El Puerto en RecorridoCompletoTest: acá no se repiten.
 *
 * <p>Como AprobacionDePedidosTest: contra el MySQL del Compose y R__datos_de_prueba.sql, sin {@code @Transactional},
 * y cada test borra lo que dejó. La seed no tiene Caja ni Cocina en El Puerto, así que sus pedidos avanzan con el
 * dominio y el PedidoRepository de verdad, como lo harían sus Services.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AislamientoEntreLocalesTest {

    private static final String MESA_1_PUERTO = "ab0124a8-6033-4d73-a1f6-3513140e62f7";
    private static final Long QUE_NO_EXISTE = 999_999L;

    @TestConfiguration
    static class ConReloj {

        @Bean
        @Primary
        RelojDePrueba relojDePrueba() {
            // Sin fracciones de segundo, como guarda la base.
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

    @Autowired
    private PedidoRepository pedidos;

    @Autowired
    private PlatformTransactionManager transacciones;

    private AyudasDeIntegracion ayudas;
    private Long pizza;
    /** Los pedidos de El Puerto, uno en cada estado que mira algún endpoint. */
    private Long pendienteDelPuerto;
    private Long aprobadoDelPuerto;
    private Long enPreparacionDelPuerto;
    private Long listoDelPuerto;
    /** El celular de La Esquina, con un pedido pendiente y otro aprobado por Carla: las colas no dan vacías. */
    private String a;
    private Long pendienteDeLaEsquina;
    private Long aprobadoDeLaEsquina;
    private int registrosAlArmar;

    @BeforeEach
    void armarPedidosEnLosDosLocales() throws Exception {
        ayudas = new AyudasDeIntegracion(mvc, jdbc, habilitaciones);
        ayudas.anotarLoQueHabia();

        pizza = ayudas.plato("Pizza muzzarella", "El Puerto");
        String delPuerto = ayudas.abrirMesa(MESA_1_PUERTO);
        pendienteDelPuerto = ayudas.pedir(delPuerto, pizza, 1, null);
        aprobadoDelPuerto = ayudas.pedir(delPuerto, pizza, 1, null);
        enPreparacionDelPuerto = ayudas.pedir(delPuerto, pizza, 1, null);
        listoDelPuerto = ayudas.pedir(delPuerto, pizza, 1, null);
        Long dueno = duenoDeElPuerto();
        Consumer<Pedido> aprobar = p -> p.aprobar(dueno, reloj.ahora());
        Consumer<Pedido> preparar = p -> p.iniciarPreparacion(reloj.ahora());
        Consumer<Pedido> terminar = p -> p.marcarListo(reloj.ahora());
        avanzar(aprobadoDelPuerto, aprobar);
        avanzar(enPreparacionDelPuerto, aprobar.andThen(preparar));
        avanzar(listoDelPuerto, aprobar.andThen(preparar).andThen(terminar));

        a = ayudas.abrirMesa(MESA_1_ESQUINA);
        pendienteDeLaEsquina = ayudas.pedir(a, ayudas.plato("Milanesa napolitana", "La Esquina"), 1, null);
        aprobadoDeLaEsquina = ayudas.pedir(a, ayudas.plato("Flan casero", "La Esquina"), 1, null);
        ayudas.aprobar(ayudas.sesionDe("Carla", "2468"), aprobadoDeLaEsquina).andExpect(status().isNoContent());
        registrosAlArmar = ayudas.registrosNuevos();
    }

    @AfterEach
    void dejarLaBaseComoEnLaSeed() {
        ayudas.borrarLoQueDejo();
    }

    // --- Caja y Cocina

    @Test
    void laColaDeCajaNoTraeLosDeElPuerto() throws Exception {
        // PedidosPorAprobarAdapterTest lo prueba en el adaptador; acá, por HTTP.
        String cola = cuerpo(mvc.perform(get("/api/v1/caja/pedidos").cookie(ayudas.sesionDe("Carla", "2468")))
                .andExpect(status().isOk()));

        assertThat(ids(cola, "$[*].id")).contains(pendienteDeLaEsquina).doesNotContain(pendienteDelPuerto);
        assertThat(cola).doesNotContain("Pizza");
    }

    @Test
    void laColaDeCocinaNoTraeLosDeElPuerto() throws Exception {
        String vista = cuerpo(mvc.perform(get("/api/v1/cocina/pedidos").cookie(ayudas.sesionDe("Hugo", "1357")))
                .andExpect(status().isOk()));

        assertThat(ids(vista, "$.enCola[*].id")).contains(aprobadoDeLaEsquina);
        // Los pedidos de las tres columnas: los platos no tienen id.
        assertThat(ids(vista, "$..id")).contains(aprobadoDeLaEsquina)
                .doesNotContain(pendienteDelPuerto, aprobadoDelPuerto, enPreparacionDelPuerto, listoDelPuerto);
        assertThat(vista).doesNotContain("Pizza");
        // Los de El Puerto están en la cola de su propio local: no faltan porque no existan.
        assertThat(pedidos.listarActivosDeCocina(ayudas.localDe("El Puerto"))).extracting(Pedido::getId)
                .containsExactlyInAnyOrder(aprobadoDelPuerto, enPreparacionDelPuerto, listoDelPuerto);
    }

    @Test
    void cocinaNoMueveUnPedidoDeElPuerto() throws Exception {
        Cookie hugo = ayudas.sesionDe("Hugo", "1357");
        Map<String, Object> aprobado = filaDe(aprobadoDelPuerto);
        Map<String, Object> enPreparacion = filaDe(enPreparacionDelPuerto);

        comoSiNoExistiera(ayudas.enPreparacion(hugo, aprobadoDelPuerto), ayudas.enPreparacion(hugo, QUE_NO_EXISTE));
        comoSiNoExistiera(ayudas.listo(hugo, aprobadoDelPuerto), ayudas.listo(hugo, QUE_NO_EXISTE));
        // El caso en que Listo andaría si no se filtrara por local.
        comoSiNoExistiera(ayudas.listo(hugo, enPreparacionDelPuerto), ayudas.listo(hugo, QUE_NO_EXISTE));

        assertThat(aprobado).containsEntry("estado", "APROBADO");
        assertThat(enPreparacion).containsEntry("estado", "EN_PREPARACION");
        assertThat(filaDe(aprobadoDelPuerto)).isEqualTo(aprobado);
        assertThat(filaDe(enPreparacionDelPuerto)).isEqualTo(enPreparacion);
        assertThat(ayudas.registrosNuevos()).isEqualTo(registrosAlArmar);
    }

    // --- El cliente

    @Test
    void elClienteDeLaEsquinaNoPuedePedirLaPizzaDeElPuerto() throws Exception {
        // El contrato no dice qué código da: el código responde 404, igual que con un plato que no existe.
        String conLaPizza = cuerpo(ayudas.enviar(a, unPedidoDe(pizza)).andExpect(status().isNotFound()));
        String queNoExiste = cuerpo(ayudas.enviar(a, unPedidoDe(QUE_NO_EXISTE)).andExpect(status().isNotFound()));

        assertThat(JsonPath.<String>read(conLaPizza, "$.detail")).isEqualTo("El plato " + pizza + " no existe");
        assertThat(JsonPath.<String>read(queNoExiste, "$.detail"))
                .isEqualTo("El plato " + QUE_NO_EXISTE + " no existe");
        assertThat(conLaPizza).doesNotContain("Pizza").doesNotContain("Puerto");
        assertThat(pedidosDe(a)).containsExactlyInAnyOrder(pendienteDeLaEsquina, aprobadoDeLaEsquina);
    }

    @Test
    void elClienteDeLaEsquinaNoPuedeCancelarUnPedidoDeElPuerto() throws Exception {
        Map<String, Object> antes = filaDe(pendienteDelPuerto);

        comoSiNoExistiera(cancelar(a, pendienteDelPuerto), cancelar(a, QUE_NO_EXISTE));

        assertThat(antes).containsEntry("estado", "PENDIENTE_APROBACION").containsEntry("cancelado_en", null);
        assertThat(filaDe(pendienteDelPuerto)).isEqualTo(antes);
    }

    @Test
    void elUuidDeUnPedidoDeElPuertoDa403SinDevolverNiTocarNada() throws Exception {
        // buscarPorUuidCliente no filtra por local y uuid_cliente es UNIQUE en toda la base, así que no puede
        // responder como si no existiera: da el 403 del pedido de otra sesión (sección 1 del contrato), sin datos
        // del pedido. Queda en el Abierto de ST-05.
        String uuid = jdbc.queryForObject("SELECT uuid_cliente FROM pedido WHERE id = ?", String.class,
                pendienteDelPuerto);
        Map<String, Object> antes = filaDe(pendienteDelPuerto);
        String cuerpo = """
                {"uuidCliente": "%s", "items": [{"platoId": %d, "cantidad": 1, "observacion": null}]}"""
                .formatted(uuid, ayudas.plato("Milanesa napolitana", "La Esquina"));

        String respuesta = cuerpo(ayudas.enviar(a, cuerpo)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Ese pedido es de otra sesión"))
                .andExpect(jsonPath("$.id").doesNotExist()));

        assertThat(respuesta).doesNotContain("Pizza").doesNotContain("Puerto");
        assertThat(filaDe(pendienteDelPuerto)).isEqualTo(antes);
        assertThat(pedidosDe(a)).containsExactlyInAnyOrder(pendienteDeLaEsquina, aprobadoDeLaEsquina);
    }

    @Test
    void elMenuDeUnaMesaDeLaEsquinaNoTraeLaPizza() throws Exception {
        String carta = cuerpo(mvc.perform(get("/api/v1/public/mesas/" + MESA_1_ESQUINA + "/menu"))
                .andExpect(status().isOk()));

        Set<Long> platos = new HashSet<>(ids(carta, "$.destacados[*].id"));
        platos.addAll(ids(carta, "$.categorias[*].platos[*].id"));
        assertThat(platos).containsExactlyInAnyOrder(ayudas.plato("Milanesa napolitana", "La Esquina"),
                ayudas.plato("Ensalada mixta", "La Esquina"), ayudas.plato("Flan casero", "La Esquina"));
        assertThat(carta).doesNotContain("Pizza");
    }

    // --- Ayudas

    /** El 404 de un id de El Puerto: el mismo detail que el de un id que no existe, y nada que nombre a El Puerto. */
    private static void comoSiNoExistiera(ResultActions deElPuerto, ResultActions queNoExiste) throws Exception {
        String delPuerto = cuerpo(deElPuerto.andExpect(status().isNotFound()));
        String inexistente = cuerpo(queNoExiste.andExpect(status().isNotFound()));

        assertThat(JsonPath.<String>read(delPuerto, "$.detail")).isEqualTo("El pedido no existe")
                .isEqualTo(JsonPath.read(inexistente, "$.detail"));
        assertThat(delPuerto).doesNotContain("Puerto").doesNotContain("Pizza");
    }

    /** El Puerto no tiene Caja ni Cocina en la seed: el pedido avanza con el dominio, en una transacción. */
    private void avanzar(Long pedidoId, Consumer<Pedido> pasos) {
        Long puerto = ayudas.localDe("El Puerto");
        new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
            Pedido pedido = pedidos.buscarPorId(pedidoId, puerto).orElseThrow();
            pasos.accept(pedido);
            pedidos.guardar(pedido);
        });
    }

    private ResultActions cancelar(String tokenSesion, Long pedidoId) throws Exception {
        Cookie xsrf = ayudas.xsrf();
        return mvc.perform(post("/api/v1/public/pedidos/" + pedidoId + "/cancelar").cookie(xsrf)
                .header("X-XSRF-TOKEN", xsrf.getValue()).header("X-Session-Token", tokenSesion));
    }

    private static String unPedidoDe(Long platoId) {
        return """
                {"uuidCliente": "%s", "items": [{"platoId": %d, "cantidad": 1, "observacion": null}]}"""
                .formatted(UUID.randomUUID(), platoId);
    }

    /** Lo que cambia cuando un pedido avanza o se cancela. */
    private Map<String, Object> filaDe(Long pedidoId) {
        return jdbc.queryForMap("""
                SELECT estado, version, resuelto_por_usuario_id, resuelto_en, en_preparacion_en, listo_en,
                cancelado_en, cancelado_por_usuario_id FROM pedido WHERE id = ?""", pedidoId);
    }

    private List<Long> pedidosDe(String tokenSesion) {
        return jdbc.queryForList("""
                SELECT p.id FROM pedido p JOIN cliente_sesion s ON s.id = p.cliente_sesion_id
                WHERE s.token_sesion = ?""", Long.class, tokenSesion);
    }

    private Long duenoDeElPuerto() {
        return jdbc.queryForObject("SELECT propietario_usuario_id FROM local WHERE nombre = 'El Puerto'", Long.class);
    }

    private static List<Long> ids(String json, String camino) {
        List<Number> ids = JsonPath.read(json, camino);
        return ids.stream().map(Number::longValue).toList();
    }

    private static String cuerpo(ResultActions respuesta) throws Exception {
        return respuesta.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
