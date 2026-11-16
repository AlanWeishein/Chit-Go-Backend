package com.chitgo.compartido.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import com.chitgo.pedidos.dominio.puertos.MesaRepository;

/**
 * RNF-15, CP-RNF15-03 (ST-25, tarea 1): un error de la base que el back no espera sale como un 500 sin el SQL, sin el
 * mensaje de MySQL y sin la pila. El error es uno de verdad: la búsqueda de la mesa corre un SELECT contra una tabla
 * que no existe. Va con el servidor levantado y no con MockMvc porque MockMvc no pasa por /error: la excepción le
 * llegaría al test y no se vería lo que recibe el cliente. Solo lee, así que la base queda como la seed.
 * <p>
 * El spy y el puerto al azar arman un contexto que no comparte nadie. Se cierra al terminar para devolver las conexiones
 * de su pool: con todos los contextos de la suite abiertos, el MySQL del Compose llega a su tope de 151.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@DirtiesContext
class ErroresDeLaBaseTest {

    private static final String TABLA_FALSA = "tabla_que_no_existe";

    /** Lo que no puede aparecer en la respuesta, en minúsculas. */
    private static final List<String> PROHIBIDO = List.of("sql", "select", TABLA_FALSA, "syntax", "doesn't exist",
            "hibernate", "jdbc", "exception", "trace", "java.", "springframework", "chitgo");

    @LocalServerPort
    private int puerto;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @MockitoSpyBean
    private MesaRepository mesas;

    private final HttpClient http = HttpClient.newHttpClient();

    private String tokenQr;

    private DataAccessException errorDeLaBase;

    @BeforeEach
    void romperLaBusquedaDeLaMesa() {
        tokenQr = jdbc.queryForObject("SELECT token_qr FROM mesa WHERE activa = TRUE ORDER BY id LIMIT 1", String.class);
        doAnswer(invocacion -> {
            try {
                return jdbc.queryForList("SELECT * FROM " + TABLA_FALSA);
            } catch (DataAccessException e) {
                errorDeLaBase = e;
                throw e;
            }
        }).when(mesas).buscarPorTokenQr(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/json", "text/html", "*/*"})
    void unErrorDeLaBaseNoDejaVerElSqlNiLaPila(String accept) throws Exception {
        HttpResponse<String> respuesta = pedirLaMesa(accept);

        assertThat(errorDeLaBase).as("la base tiró el error de verdad, con el SQL adentro").isNotNull();
        assertThat(errorDeLaBase.getMessage()).contains(TABLA_FALSA);
        assertThat(respuesta.statusCode()).isEqualTo(500);
        assertThat(respuesta.body().toLowerCase()).doesNotContain(PROHIBIDO);
    }

    @Test
    void enJsonSoloVanLosCamposDeSiempre() throws Exception {
        HttpResponse<String> respuesta = pedirLaMesa("application/json");

        Map<String, Object> cuerpo = json.readValue(respuesta.body(), new TypeReference<>() { });
        assertThat(cuerpo.keySet()).containsExactlyInAnyOrder("timestamp", "status", "error", "path");
        assertThat(cuerpo.get("status")).isEqualTo(500);
    }

    private HttpResponse<String> pedirLaMesa(String accept) throws Exception {
        HttpRequest pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/v1/public/mesas/" + tokenQr))
                .header("Accept", accept).GET().build();
        return http.send(pedido, HttpResponse.BodyHandlers.ofString());
    }
}
