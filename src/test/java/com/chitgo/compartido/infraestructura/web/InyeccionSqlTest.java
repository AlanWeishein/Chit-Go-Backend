package com.chitgo.compartido.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import com.chitgo.usuarios.infraestructura.seguridad.CookieDeSesion;

/**
 * RNF-15, tarea 2 de la ST-25 (CP-RNF15-01, CP-11-07 y CP-25-08): cada entrada maliciosa en cada campo de texto y
 * cada filtro, con el login de verdad, contra el MySQL del Compose y la seed. Sin {@code @Transactional}, como
 * DisponibilidadDePlatosTest, y con sus mismas anotaciones para usar el mismo contexto y no abrir otro pool contra
 * el tope de conexiones del Compose.
 *
 * <p>Cada pedido malicioso va entre dos fotos de la base: de cada tabla, la cantidad de filas y el
 * {@code CHECKSUM TABLE}. Si se rechaza o solo lee, las fotos son iguales. Si guarda, cambian solo las tablas
 * esperadas, con las filas esperadas de más, y las filas que ya estaban siguen iguales. Al terminar, cada test borra
 * lo que dejó y comprueba que todas las tablas tienen el mismo checksum que al empezar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class InyeccionSqlTest {

    private static final String TOKEN_LA_ESQUINA = "83f39b25-deac-4fde-80cc-4cfdf8117713";
    private static final String MESA_1_ESQUINA = "d4964b6b-fdae-4b4f-83e9-1c1e78a21723";
    private static final String CONTRASENA = "Chitgo2026!";
    private static final String DATOS_INVALIDOS = "Los datos enviados no son válidos.";
    private static final Duration TOPE = Duration.ofSeconds(3);

    /** Las de CP-RNF15-01 y tres de OWASP. Todas de hasta 36 caracteres, para que el largo no las corte antes. */
    static Stream<String> entradas() {
        return Stream.of(
                "' OR '1'='1",
                "' OR '1'='1' --",
                "'; DROP TABLE pedido; --",
                "' UNION SELECT NULL, NULL --",
                "') OR ('1'='1",
                "' AND SLEEP(3)#",
                "\\'; DROP TABLE plato; --");
    }

    /** Lo que se borra al terminar, en el orden de las FK. */
    private static final List<String> TABLAS_QUE_CRECEN = List.of("registro_auditoria", "ticket", "item_pedido",
            "pedido", "cliente_sesion", "aviso", "cuenta", "plato", "etiqueta", "empleado", "usuario");

    /** Lo que deja ver un error de la base: el driver, el ORM, el SQL o una tabla nombrada como en una consulta. */
    private static final List<String> PALABRAS_PROHIBIDAS = List.of("sql", "hibernate", "jdbc", "syntax",
            "exception", "constraint", "stacktrace");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    private List<String> tablas;
    private List<String> tablasConId;
    private Map<String, Long> ultimoIdAntes;
    private Map<String, Long> checksumAlEmpezar;
    private List<Map<String, Object>> usuariosDeLaSeed;
    private List<Map<String, Object>> mesasDeLaSeed;
    private Pattern tablaNombrada;

    @BeforeEach
    void anotarComoEstaLaSeed() {
        tablas = jdbc.queryForList("""
                SELECT TABLE_NAME FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME""", String.class);
        tablasConId = jdbc.queryForList("""
                SELECT TABLE_NAME FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'id'""", String.class);
        ultimoIdAntes = new HashMap<>();
        for (String tabla : TABLAS_QUE_CRECEN) {
            ultimoIdAntes.put(tabla, maximo(tabla));
        }
        checksumAlEmpezar = checksums();
        usuariosDeLaSeed = jdbc.queryForList("SELECT * FROM usuario");
        mesasDeLaSeed = jdbc.queryForList("SELECT id, inhabilitada, motivo_inhabilitacion, version FROM mesa");
        String esquema = jdbc.queryForObject("SELECT DATABASE()", String.class);
        String nombres = String.join("|", tablas);
        // "FROM pedido", "TABLE `plato`", "chitgo.usuario" o "pedido.estado". "La mesa no existe" no cuenta: es
        // una palabra de la API, no una consulta.
        tablaNombrada = Pattern.compile("(?i)(\\b(from|into|update|join|table)\\s+[`'\"]?(" + nombres + ")\\b)"
                + "|(\\b" + Pattern.quote(esquema) + "\\.)|(\\b(" + nombres + ")\\.[a-z_])");
    }

    @AfterEach
    void dejarLaBaseComoLaSeed() {
        for (String tabla : TABLAS_QUE_CRECEN) {
            jdbc.update("DELETE FROM " + tabla + " WHERE id > ?", ultimoIdAntes.get(tabla));
        }
        for (Map<String, Object> fila : usuariosDeLaSeed) {
            List<String> columnas = fila.keySet().stream().filter(c -> !c.equals("id")).toList();
            List<Object> valores = new ArrayList<>(columnas.stream().map(fila::get).toList());
            valores.add(fila.get("id"));
            jdbc.update("UPDATE usuario SET " + columnas.stream().map(c -> c + " = ?").collect(Collectors.joining(", "))
                    + " WHERE id = ?", valores.toArray());
        }
        for (Map<String, Object> m : mesasDeLaSeed) {
            jdbc.update("UPDATE mesa SET inhabilitada = ?, motivo_inhabilitacion = ?, version = ? WHERE id = ?",
                    m.get("inhabilitada"), m.get("motivo_inhabilitacion"), m.get("version"), m.get("id"));
        }
        assertThat(checksums()).as("la base vuelve a como estaba al empezar el test").isEqualTo(checksumAlEmpezar);
    }

    // --- Los logins y los QR: se rechaza, y la base no cambia

    @ParameterizedTest
    @MethodSource("entradas")
    void elEmailDelLoginDelAdminDa400(String entrada) throws Exception {
        // CP-11-07: desde el 16/11 el formato del email se valida antes de autenticar.
        Foto antes = foto();

        MvcResult r = malicioso(entrada, enviar(post("/api/v1/auth/admin/login"),
                Map.of("email", entrada, "password", "cualquierCosa")));

        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(detalle(r)).isEqualTo(DATOS_INVALIDOS);
        assertThat(r.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(foto().cambiosDesde(antes)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("entradas")
    void elUsuarioDelLoginDelMozoDa401Generico(String entrada) throws Exception {
        Foto antes = foto();

        MvcResult r = malicioso(entrada, enviar(post("/api/v1/auth/mozo/login"),
                Map.of("tokenStaff", TOKEN_LA_ESQUINA, "nombreUsuario", entrada, "password", "cualquierCosa")));

        assertThat(r.getResponse().getStatus()).isEqualTo(401);
        assertThat(detalle(r)).isEqualTo("Usuario o contraseña incorrectos");
        assertThat(r.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(foto().cambiosDesde(antes)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("entradas")
    void elTokenStaffDeLaRutaDa404(String entrada) throws Exception {
        Foto antes = foto();

        MvcResult r = malicioso(entrada, get("/api/v1/public/staff/{tokenStaff}", entrada));

        // 404 si llega al controller; 400 si el firewall de Spring Security corta antes la URL (';' o '\').
        assertThat(r.getResponse().getStatus()).isIn(400, 404);
        assertThat(foto().cambiosDesde(antes)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("entradas")
    void elTokenStaffDelPinDa404(String entrada) throws Exception {
        // Con Carla, que es de Caja y no necesita la cookie de dispositivo.
        Foto antes = foto();

        MvcResult r = malicioso(entrada, enviar(post("/api/v1/auth/pin"),
                Map.of("tokenStaff", entrada, "usuarioId", idDe("Carla"), "pin", "2468")));

        assertThat(r.getResponse().getStatus()).isEqualTo(404);
        assertThat(detalle(r)).isEqualTo("El QR de staff no es válido.");
        assertThat(r.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(foto().cambiosDesde(antes)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("entradas")
    void elTokenQrDeLaRutaDa404(String entrada) throws Exception {
        Foto antes = foto();

        MvcResult r = malicioso(entrada, get("/api/v1/public/mesas/{tokenQr}", entrada));

        assertThat(r.getResponse().getStatus()).isIn(400, 404);
        assertThat(foto().cambiosDesde(antes)).isEmpty();
    }

    // --- Los datos que se guardan: tal cual, y solo lo esperado

    @ParameterizedTest
    @MethodSource("entradas")
    void laObservacionSeGuardaTalCualYCajaLaVeIgual(String entrada) throws Exception {
        String tokenSesion = abrirMesa();
        Long milanesa = plato("Milanesa napolitana");
        Map<String, Object> cuentaAntes = jdbc.queryForMap("SELECT * FROM cuenta WHERE id = (SELECT MAX(id) FROM cuenta)");
        Foto antes = foto();

        MvcResult r = malicioso(entrada, enviar(post("/api/v1/public/pedidos").header("X-Session-Token", tokenSesion),
                Map.of("uuidCliente", UUID.randomUUID().toString(),
                        "items", List.of(Map.of("platoId", milanesa, "cantidad", 1, "observacion", entrada)))));

        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        Long pedido = id(r);
        assertThat(jdbc.queryForObject("SELECT observacion FROM item_pedido WHERE pedido_id = ?", String.class,
                pedido)).isEqualTo(entrada);
        // La cuenta no suma filas: EnviarPedidoService le sube la version (control de concurrencia), y nada más.
        foto().cambiosDesde(antes).esperados(Map.of("pedido", 1, "item_pedido", 1, "cuenta", 0));
        Map<String, Object> cuentaDespues = jdbc.queryForMap("SELECT * FROM cuenta WHERE id = ?", cuentaAntes.get("id"));
        assertThat(((Number) cuentaDespues.get("version")).intValue())
                .isEqualTo(((Number) cuentaAntes.get("version")).intValue() + 1);
        cuentaAntes.remove("version");
        cuentaDespues.remove("version");
        assertThat(cuentaDespues).isEqualTo(cuentaAntes);

        String cola = cuerpo(mvc.perform(get("/api/v1/caja/pedidos").cookie(sesionDeCarla()))
                .andExpect(status().isOk()).andReturn());
        assertThat(JsonPath.<List<String>>read(cola, "$[?(@.id == " + pedido + ")].items[*].observacion"))
                .containsExactly(entrada);
    }

    @ParameterizedTest
    @MethodSource("entradas")
    void elNombreDeUnPlatoSeGuardaTalCual(String entrada) throws Exception {
        // CP-25-08 con todas las entradas; el caso tal cual lo cubre CartaDelAdminTest.
        Cookie admin = sesionConEmail("admin@laesquina.test");
        Foto antes = foto();

        MvcResult r = malicioso(entrada, enviar(post("/api/v1/admin/platos").cookie(admin),
                Map.of("nombre", entrada, "precio", 9500, "categoriaId", categoria("Principales"),
                        "etiquetaIds", List.of())));

        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        assertThat(JsonPath.<String>read(cuerpo(r), "$.nombre")).isEqualTo(entrada);
        assertThat(jdbc.queryForObject("SELECT nombre FROM plato WHERE id = ?", String.class, id(r))).isEqualTo(entrada);
        foto().cambiosDesde(antes).esperados(Map.of("plato", 1, "registro_auditoria", 1));
    }

    @ParameterizedTest
    @MethodSource("entradas")
    void elNombreDeUnEmpleadoSeGuardaTalCual(String entrada) throws Exception {
        // Caja, que no lleva usuario ni contraseña.
        Cookie admin = sesionConEmail("admin@laesquina.test");
        Foto antes = foto();

        MvcResult r = malicioso(entrada, enviar(post("/api/v1/admin/empleados").cookie(admin),
                Map.of("nombre", entrada, "rol", "CAJA", "pin", "8642")));

        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT u.nombre FROM empleado e JOIN usuario u ON u.id = e.usuario_id "
                + "WHERE e.id = ?", String.class, id(r))).isEqualTo(entrada);
        foto().cambiosDesde(antes).esperados(Map.of("usuario", 1, "empleado", 1, "registro_auditoria", 1));
    }

    @ParameterizedTest
    @MethodSource("entradas")
    void elUsuarioDeUnMozoSeGuardaTalCualYNoChoca(String entrada) throws Exception {
        Cookie admin = sesionConEmail("admin@laesquina.test");
        Map<String, Object> alta = Map.of("nombre", "Mozo de prueba", "rol", "MOZO", "pin", "8642",
                "nombreUsuario", entrada, "password", CONTRASENA);
        Foto antes = foto();

        // No choca con "juan" ni con nadie: el existe compara el texto.
        MvcResult r = malicioso(entrada, enviar(post("/api/v1/admin/empleados").cookie(admin), alta));

        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT u.nombre_usuario FROM empleado e JOIN usuario u ON u.id = e.usuario_id "
                + "WHERE e.id = ?", String.class, id(r))).isEqualTo(entrada);
        foto().cambiosDesde(antes).esperados(Map.of("usuario", 1, "empleado", 1, "registro_auditoria", 1));

        // Con el mismo texto sí choca, y no guarda nada.
        Foto conElPrimero = foto();
        MvcResult repetido = malicioso(entrada, enviar(post("/api/v1/admin/empleados").cookie(admin), alta));
        assertThat(repetido.getResponse().getStatus()).isEqualTo(409);
        assertThat(foto().cambiosDesde(conElPrimero)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("entradas")
    void elEmailDeUnAdminNuevoSinFormatoDa400(String entrada) throws Exception {
        Cookie dueno = sesionConEmail("dueno@laesquina.test");
        Foto antes = foto();

        MvcResult r = malicioso(entrada, enviar(post("/api/v1/gerente/admins").cookie(dueno),
                Map.of("nombre", "Admin de prueba", "email", entrada, "password", CONTRASENA)));

        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(detalle(r)).isEqualTo(DATOS_INVALIDOS);
        assertThat(foto().cambiosDesde(antes)).isEmpty();
    }

    @Test
    void unEmailValidoConComillasSeGuardaTalCualYRepetidoDa409() throws Exception {
        // La comilla está permitida antes de la arroba: así la entrada llega a existeEmail y al INSERT.
        Cookie dueno = sesionConEmail("dueno@laesquina.test");
        for (String email : List.of("x'or'1'='1@laesquina.test", "admin'--@laesquina.test")) {
            Map<String, Object> alta = Map.of("nombre", "Admin de prueba", "email", email, "password", CONTRASENA);
            Foto antes = foto();

            MvcResult r = malicioso(email, enviar(post("/api/v1/gerente/admins").cookie(dueno), alta));

            assertThat(r.getResponse().getStatus()).isEqualTo(201);
            assertThat(jdbc.queryForObject("SELECT email FROM usuario WHERE id = ?", String.class, id(r)))
                    .isEqualTo(email);
            foto().cambiosDesde(antes).esperados(Map.of("usuario", 1, "empleado", 1, "registro_auditoria", 1));

            Foto conElPrimero = foto();
            MvcResult repetido = malicioso(email, enviar(post("/api/v1/gerente/admins").cookie(dueno), alta));
            assertThat(repetido.getResponse().getStatus()).isEqualTo(409);
            assertThat(detalle(repetido)).isEqualTo("Ya hay un usuario con ese email");
            assertThat(foto().cambiosDesde(conElPrimero)).isEmpty();
        }
    }

    @ParameterizedTest
    @MethodSource("entradas")
    void unMotivoDeRechazoQueNoEsDeLaListaDa400(String entrada) throws Exception {
        // El motivo es el enum MotivoRechazo, no texto libre: lo que no es de la lista no llega a la base.
        Long pedido = pedir(abrirMesa());
        Cookie carla = sesionDeCarla();
        Foto antes = foto();

        MvcResult r = malicioso(entrada, enviar(post("/api/v1/caja/pedidos/{id}/rechazar", pedido).cookie(carla),
                Map.of("motivo", entrada)));

        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(detalle(r)).isEqualTo(DATOS_INVALIDOS);
        assertThat(jdbc.queryForObject("SELECT estado FROM pedido WHERE id = ?", String.class, pedido))
                .isEqualTo("PENDIENTE_APROBACION");
        assertThat(foto().cambiosDesde(antes)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("entradas")
    void elNombreDeUnaEtiquetaSeGuardaTalCualYRepetidoDa409(String entrada) throws Exception {
        Cookie admin = sesionConEmail("admin@laesquina.test");
        Foto antes = foto();

        MvcResult r = malicioso(entrada, enviar(post("/api/v1/admin/etiquetas").cookie(admin),
                Map.of("nombre", entrada)));

        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT nombre FROM etiqueta WHERE id = ?", String.class, id(r)))
                .isEqualTo(entrada);
        foto().cambiosDesde(antes).esperados(Map.of("etiqueta", 1, "registro_auditoria", 1));

        Foto conLaPrimera = foto();
        MvcResult repetida = malicioso(entrada, enviar(post("/api/v1/admin/etiquetas").cookie(admin),
                Map.of("nombre", entrada)));
        assertThat(repetida.getResponse().getStatus()).isEqualTo(409);
        assertThat(foto().cambiosDesde(conLaPrimera)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("entradas")
    void elMotivoDeInhabilitacionDeUnaMesaSeGuardaTalCual(String entrada) throws Exception {
        Cookie admin = sesionConEmail("admin@laesquina.test");
        Long mesa6 = jdbc.queryForObject("""
                SELECT m.id FROM mesa m JOIN local l ON l.id = m.local_id
                WHERE m.numero = '6' AND l.nombre = 'La Esquina'""", Long.class);
        Foto antes = foto();

        MvcResult r = malicioso(entrada, enviar(patch("/api/v1/admin/mesas/{id}/inhabilitada", mesa6).cookie(admin),
                Map.of("inhabilitada", true, "motivo", entrada)));

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT motivo_inhabilitacion FROM mesa WHERE id = ?", String.class, mesa6))
                .isEqualTo(entrada);
        // La mesa se modifica (no suma filas) y queda el registro de auditoría.
        foto().cambiosDesde(antes).esperados(Map.of("mesa", 0, "registro_auditoria", 1));
    }

    // --- Los filtros

    @ParameterizedTest
    @MethodSource("entradas")
    void elEstadoDeLaColaYDeLasCuentasDeCajaDa400(String entrada) throws Exception {
        Cookie carla = sesionDeCarla();
        Foto antes = foto();

        for (String ruta : List.of("/api/v1/caja/pedidos", "/api/v1/caja/cuentas")) {
            MvcResult r = malicioso(entrada, get(ruta).param("estado", entrada).cookie(carla));
            assertThat(r.getResponse().getStatus()).as(ruta).isEqualTo(400);
        }
        assertThat(foto().cambiosDesde(antes)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("entradas")
    void losFiltrosDeTextoDeLaAuditoriaNoTraenFilasDeMas(String entrada) throws Exception {
        // Un registro con la entrada como entidad y acción en cada local. Tiene que volver solo el de La Esquina.
        Long esquina = local("La Esquina");
        Long puerto = local("El Puerto");
        Long deLaEsquina = registro(esquina, usuarioPorEmail("admin@laesquina.test"), entrada);
        registro(puerto, jdbc.queryForObject("SELECT MIN(id) FROM usuario WHERE local_id = ?", Long.class, puerto),
                entrada);
        Cookie admin = sesionConEmail("admin@laesquina.test");
        Cookie dueno = sesionConEmail("dueno@laesquina.test");
        Foto antes = foto();

        for (String filtro : List.of("entidad", "accion")) {
            for (MockHttpServletRequestBuilder pedido : List.of(
                    get("/api/v1/admin/auditoria").cookie(admin),
                    get("/api/v1/gerente/auditoria").cookie(dueno),
                    get("/api/v1/gerente/auditoria").cookie(dueno).param("localId", esquina.toString()))) {
                MvcResult r = malicioso(entrada, pedido.param(filtro, entrada));
                assertThat(r.getResponse().getStatus()).as(filtro).isEqualTo(200);
                String cuerpo = cuerpo(r);
                assertThat(JsonPath.<Integer>read(cuerpo, "$.totalElementos")).as(filtro).isEqualTo(1);
                assertThat(JsonPath.<List<Number>>read(cuerpo, "$.contenido[*].id"))
                        .extracting(Number::longValue).containsExactly(deLaEsquina);
            }
            // Un texto que no es igual no trae nada: compara el texto entero, no lo interpreta.
            MvcResult otro = malicioso(entrada, get("/api/v1/admin/auditoria").cookie(admin)
                    .param(filtro, entrada + "x"));
            assertThat(JsonPath.<Integer>read(cuerpo(otro), "$.totalElementos")).isZero();
        }
        assertThat(foto().cambiosDesde(antes)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("entradas")
    void losFiltrosConTipoDeAuditoriaYReportesDan400(String entrada) throws Exception {
        Cookie admin = sesionConEmail("admin@laesquina.test");
        Cookie dueno = sesionConEmail("dueno@laesquina.test");
        String esquina = local("La Esquina").toString();
        Map<String, String> periodo = Map.of("desde", "2026-11-01", "hasta", "2026-11-30");
        List<String> deAuditoria = List.of("localId", "usuarioId", "desde", "hasta", "page", "size");
        List<String> deReportes = List.of("localId", "desde", "hasta", "page", "size");
        Map<String, List<String>> rutas = new LinkedHashMap<>();
        rutas.put("/api/v1/admin/auditoria", deAuditoria);
        rutas.put("/api/v1/gerente/auditoria", deAuditoria);
        for (String reporte : List.of("ventas", "tiempos-preparacion", "platos-mas-pedidos")) {
            rutas.put("/api/v1/admin/reportes/" + reporte, deReportes);
            rutas.put("/api/v1/gerente/reportes/" + reporte, deReportes);
        }
        Foto antes = foto();

        for (Map.Entry<String, List<String>> ruta : rutas.entrySet()) {
            boolean delGerente = ruta.getKey().contains("/gerente/");
            for (String parametro : ruta.getValue()) {
                Map<String, String> parametros = new LinkedHashMap<>(periodo);
                if (delGerente) {
                    parametros.put("localId", esquina);
                }
                parametros.put(parametro, entrada);
                MockHttpServletRequestBuilder pedido = get(ruta.getKey()).cookie(delGerente ? dueno : admin);
                parametros.forEach(pedido::param);

                MvcResult r = malicioso(entrada, pedido);
                assertThat(r.getResponse().getStatus()).as(ruta.getKey() + "?" + parametro).isEqualTo(400);
            }
        }
        assertThat(foto().cambiosDesde(antes)).isEmpty();
    }

    // --- El pedido malicioso: nunca un 500, nada que deje ver la base, y sin esperar al SLEEP

    private MvcResult malicioso(String entrada, MockHttpServletRequestBuilder pedido) throws Exception {
        long inicio = System.nanoTime();
        MvcResult r = mvc.perform(pedido).andReturn();
        Duration demora = Duration.ofNanos(System.nanoTime() - inicio);
        MockHttpServletResponse respuesta = r.getResponse();

        assertThat(respuesta.getStatus()).as("nunca un 500").isLessThan(500);
        assertThat(demora).as("el SLEEP no corre").isLessThan(TOPE);
        // El cuerpo y, si el error salió por sendError, también su mensaje.
        String texto = sinLoQueSeMando(respuesta.getContentAsString(StandardCharsets.UTF_8)
                + " " + (respuesta.getErrorMessage() == null ? "" : respuesta.getErrorMessage()), entrada, r);
        for (String palabra : PALABRAS_PROHIBIDAS) {
            assertThat(texto).as("la respuesta no dice " + palabra).doesNotContainIgnoringCase(palabra);
        }
        assertThat(tablaNombrada.matcher(texto).find()).as("la respuesta no nombra una tabla: " + texto).isFalse();
        return r;
    }

    /** La respuesta sin la entrada (tal cual o en JSON) ni la ruta, que pueden traer "DROP TABLE pedido" o "/pedidos". */
    private String sinLoQueSeMando(String texto, String entrada, MvcResult r) {
        String enJson = json.writeValueAsString(entrada);
        return texto.replace(enJson.substring(1, enJson.length() - 1), "")
                .replace(entrada, "")
                .replace(r.getRequest().getRequestURI(), "");
    }

    // --- Las fotos de la base

    private record Tabla(long filas, long checksum) {
    }

    private final class Foto {

        private final Map<String, Tabla> tablasDeLaFoto;
        private final Map<String, List<Map<String, Object>>> filas;

        private Foto(Map<String, Tabla> tablasDeLaFoto, Map<String, List<Map<String, Object>>> filas) {
            this.tablasDeLaFoto = tablasDeLaFoto;
            this.filas = filas;
        }

        /** Las tablas que cambiaron, con cuántas filas de más tienen. */
        Cambios cambiosDesde(Foto antes) {
            Map<String, Integer> cambios = new LinkedHashMap<>();
            tablasDeLaFoto.forEach((tabla, ahora) -> {
                Tabla antesDeLaTabla = antes.tablasDeLaFoto.get(tabla);
                if (antesDeLaTabla.checksum() != ahora.checksum() || antesDeLaTabla.filas() != ahora.filas()) {
                    cambios.put(tabla, (int) (ahora.filas() - antesDeLaTabla.filas()));
                }
            });
            return new Cambios(cambios, antes, this);
        }
    }

    private static final class Cambios extends LinkedHashMap<String, Integer> {

        private final transient Foto antes;
        private final transient Foto despues;

        Cambios(Map<String, Integer> cambios, Foto antes, Foto despues) {
            super(cambios);
            this.antes = antes;
            this.despues = despues;
        }

        /** Cambiaron solo estas tablas, con estas filas de más, y donde se sumaron, las que ya estaban siguen iguales. */
        void esperados(Map<String, Integer> esperados) {
            assertThat(this).as("las tablas que cambiaron").isEqualTo(esperados);
            esperados.forEach((tabla, deMas) -> {
                if (deMas > 0 && antes.filas.containsKey(tabla)) {
                    List<Map<String, Object>> ahora = despues.filas.get(tabla);
                    assertThat(ahora.subList(0, antes.filas.get(tabla).size()))
                            .as("las filas que ya estaban en " + tabla).isEqualTo(antes.filas.get(tabla));
                }
            });
        }
    }

    private Foto foto() {
        Map<String, Long> checksums = checksums();
        Map<String, Tabla> tablasDeLaFoto = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> filas = new HashMap<>();
        for (String tabla : tablas) {
            long cantidad = jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Long.class);
            tablasDeLaFoto.put(tabla, new Tabla(cantidad, checksums.get(tabla)));
            if (tablasConId.contains(tabla)) {
                filas.put(tabla, jdbc.queryForList("SELECT * FROM " + tabla + " ORDER BY id").stream()
                        .map(InyeccionSqlTest::comparable).toList());
            }
        }
        return new Foto(tablasDeLaFoto, filas);
    }

    /** Un byte[] no se compara por contenido con equals: va en hexa. */
    private static Map<String, Object> comparable(Map<String, Object> fila) {
        Map<String, Object> copia = new LinkedHashMap<>();
        fila.forEach((columna, valor) -> copia.put(columna,
                valor instanceof byte[] bytes ? HexFormat.of().formatHex(bytes) : valor));
        return copia;
    }

    private Map<String, Long> checksums() {
        Map<String, Long> checksums = new LinkedHashMap<>();
        jdbc.query("CHECKSUM TABLE " + String.join(", ", tablas), fila -> {
            String tabla = fila.getString(1);
            checksums.put(tabla.substring(tabla.indexOf('.') + 1), fila.getLong(2));
        });
        return checksums;
    }

    // --- Ayudas: el armado

    private String abrirMesa() throws Exception {
        MvcResult r = mvc.perform(conCsrf(post("/api/v1/public/mesas/" + MESA_1_ESQUINA + "/sesiones")))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(cuerpo(r), "$.tokenSesion");
    }

    private Long pedir(String tokenSesion) throws Exception {
        MvcResult r = mvc.perform(enviar(post("/api/v1/public/pedidos").header("X-Session-Token", tokenSesion),
                        Map.of("uuidCliente", UUID.randomUUID().toString(),
                                "items", List.of(Map.of("platoId", plato("Milanesa napolitana"), "cantidad", 1)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE_APROBACION"))
                .andReturn();
        return id(r);
    }

    private Long registro(Long localId, Long usuarioId, String texto) {
        jdbc.update("INSERT INTO registro_auditoria (local_id, usuario_id, accion, entidad) VALUES (?, ?, ?, ?)",
                localId, usuarioId, texto, texto);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    // --- Ayudas: el login de verdad

    private Cookie sesionConEmail(String email) throws Exception {
        Cookie sesion = mvc.perform(enviar(post("/api/v1/auth/admin/login"),
                        Map.of("email", email, "password", CONTRASENA)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
        assertThat(sesion).isNotNull();
        return sesion;
    }

    /** Caja entra con el PIN sin la cookie de dispositivo, como en IngresoDeMozoTest. */
    private Cookie sesionDeCarla() throws Exception {
        Cookie sesion = mvc.perform(enviar(post("/api/v1/auth/pin"),
                        Map.of("tokenStaff", TOKEN_LA_ESQUINA, "usuarioId", idDe("Carla"), "pin", "2468")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
        assertThat(sesion).isNotNull();
        return sesion;
    }

    /** Con la cookie y el header de CSRF y el cuerpo en JSON, como cualquier POST o PATCH del front. */
    private MockHttpServletRequestBuilder enviar(MockHttpServletRequestBuilder pedido, Object cuerpo) throws Exception {
        return conCsrf(pedido).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo));
    }

    private MockHttpServletRequestBuilder conCsrf(MockHttpServletRequestBuilder pedido) throws Exception {
        Cookie xsrf = mvc.perform(get("/api/v1/ping")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return pedido.cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue());
    }

    private static String cuerpo(MvcResult r) throws Exception {
        return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String detalle(MvcResult r) throws Exception {
        return JsonPath.read(cuerpo(r), "$.detail");
    }

    private static Long id(MvcResult r) throws Exception {
        return ((Number) JsonPath.read(cuerpo(r), "$.id")).longValue();
    }

    // --- Ayudas: la base

    private long maximo(String tabla) {
        return jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM " + tabla, Long.class);
    }

    private Long idDe(String nombre) {
        return jdbc.queryForObject("""
                SELECT u.id FROM usuario u JOIN local l ON l.id = u.local_id
                WHERE u.nombre = ? AND l.nombre = 'La Esquina'""", Long.class, nombre);
    }

    private Long usuarioPorEmail(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
    }

    private Long local(String nombre) {
        return jdbc.queryForObject("SELECT id FROM local WHERE nombre = ?", Long.class, nombre);
    }

    private Long plato(String nombre) {
        return jdbc.queryForObject("""
                SELECT p.id FROM plato p JOIN local l ON l.id = p.local_id
                WHERE p.nombre = ? AND l.nombre = 'La Esquina'""", Long.class, nombre);
    }

    private Long categoria(String nombre) {
        return jdbc.queryForObject("""
                SELECT c.id FROM categoria_menu c JOIN local l ON l.id = c.local_id
                WHERE c.nombre = ? AND l.nombre = 'La Esquina'""", Long.class, nombre);
    }
}
