package com.chitgo.carga;

import static org.assertj.core.api.Assertions.assertThat;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.Statement;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.chitgo.usuarios.dominio.DatosDeSesion;
import com.chitgo.usuarios.dominio.puertos.CodificadorDeClaves;
import com.chitgo.usuarios.dominio.puertos.EmisorDeTokens;

/** Opt-in: mvn -Pcarga test. HTTP y WebSocket reales, MySQL, fixtures propios y sin mocks. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
        "debug=false", "logging.level.root=WARN", "logging.level.org.springframework=WARN", "logging.level.org.hibernate=WARN"})
@ActiveProfiles("dev")
class CargaSprint3IT {
    @LocalServerPort int puerto;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired EmisorDeTokens tokens;
    @Autowired CodificadorDeClaves claves;
    @Autowired SimpUserRegistry usuariosStomp;
    Long local, plato;
    String tokenCocina;
    Stomp kds;
    final List<Dispositivo> clientes = new ArrayList<>();
    final List<Double> latencias = new ArrayList<>();

    @Test
    void treintaMesasConcurrentesYDiezPedidosPorMinutoLleganAlKds() throws Exception {
        int minutos = Integer.getInteger("carga.minutos", 1);
        assertThat(minutos).isBetween(1, 30);
        preparar();
        Dispositivo caja = personal("CAJA");
        Dispositivo cocina = personal("COCINA");
        kds = new Stomp(tokenCocina);
        kds.conectar();
        String destino = "/topic/local/" + local + "/cocina/cola";
        kds.enviar("SUBSCRIBE\nid:kds\ndestination:" + destino + "\n\n\0");
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (usuariosStomp.findSubscriptions(s -> destino.equals(s.getDestination())).isEmpty()) {
            if (System.nanoTime() > limite) throw new AssertionError("El KDS no logró suscribirse");
            Thread.sleep(20);
        }

        // Las 30 cuentas ya están activas cuando se dispara la ráfaga de envíos.
        var inicio = new CountDownLatch(1);
        var preparados = new CountDownLatch(30);
        var pedidos = new ArrayList<Future<Long>>();
        try (var hilos = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Dispositivo cliente : clientes) pedidos.add(hilos.submit(() -> {
                preparados.countDown(); inicio.await(); return cliente.pedir();
            }));
            assertThat(preparados.await(10, TimeUnit.SECONDS)).isTrue();
            inicio.countDown();
            var ids = new HashSet<Long>();
            for (Future<Long> pedido : pedidos) ids.add(pedido.get(30, TimeUnit.SECONDS));
            assertThat(ids).hasSize(30);
            assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT cuenta_id) FROM pedido WHERE local_id=?", Long.class, local)).isEqualTo(30);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pedido p JOIN cuenta c ON c.id=p.cuenta_id "
                    + "JOIN cliente_sesion s ON s.id=p.cliente_sesion_id WHERE p.local_id=? "
                    + "AND (p.local_id<>c.local_id OR p.local_id<>s.local_id OR p.cuenta_id<>s.cuenta_id)", Long.class, local)).isZero();
            for (Long id : ids) aprobarYMedir(caja, id);
        }
        assertThat(cocina.get("/api/v1/cocina/pedidos").get("enCola").size()).isEqualTo(30);
        double p95Rafaga = p95(latencias);

        long inicioSostenido = System.nanoTime();
        int cantidad = minutos * 10;
        for (int i = 0; i < cantidad; i++) {
            esperarHasta(inicioSostenido + TimeUnit.SECONDS.toNanos(i * 6L));
            aprobarYMedir(caja, clientes.get(i % 30).pedir());
        }
        esperarHasta(inicioSostenido + TimeUnit.MINUTES.toNanos(minutos));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pedido WHERE local_id=?", Long.class, local)).isEqualTo(30L + cantidad);
        assertThat(cocina.get("/api/v1/cocina/pedidos").get("enCola").size()).isEqualTo(30 + cantidad);
        double p95Sostenido = p95(latencias.subList(30, latencias.size()));
        var resultado = new LinkedHashMap<String, Object>();
        resultado.put("medidoEn", Instant.now().toString());
        resultado.put("entorno", "HTTP + STOMP en loopback, MySQL Docker; no incluye render del navegador ni Wi-Fi del local");
        resultado.put("mesasActivas", 30); resultado.put("pedidosRafaga", 30);
        resultado.put("minutosSostenidos", minutos); resultado.put("pedidosPorMinuto", 10);
        resultado.put("pedidosSostenidos", cantidad); resultado.put("pedidosPerdidosOMezclados", 0);
        resultado.put("p95RafagaMs", p95Rafaga); resultado.put("p95SostenidoMs", p95Sostenido);
        resultado.put("umbralMs", 2000); resultado.put("cumple", p95Rafaga <= 2000 && p95Sostenido <= 2000);
        Files.writeString(Path.of("target/carga-sprint3.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(resultado));
        System.out.println("Carga Sprint 3: " + json.writeValueAsString(resultado));
        assertThat(p95Rafaga).isLessThanOrEqualTo(2000);
        assertThat(p95Sostenido).isLessThanOrEqualTo(2000);
    }

    private void preparar() throws Exception {
        Long propietario = jdbc.queryForObject("SELECT MIN(id) FROM usuario WHERE local_id IS NULL", Long.class);
        local = insertar("INSERT INTO local(propietario_usuario_id,nombre,token_staff) VALUES(?,?,?)", propietario,
                "Carga " + UUID.randomUUID().toString().substring(0, 8), UUID.randomUUID().toString());
        Long categoria = insertar("INSERT INTO categoria_menu(local_id,nombre,orden) VALUES(?,'Carga',1)", local);
        plato = insertar("INSERT INTO plato(local_id,categoria_id,nombre,precio,activo) VALUES(?,?,'Carga',100,true)", local, categoria);
        for (int i = 0; i < 30; i++) {
            String qr = UUID.randomUUID().toString();
            Long mesa = insertar("INSERT INTO mesa(local_id,numero,token_qr) VALUES(?,?,?)", local, "Carga-" + i, qr);
            var cliente = new Dispositivo(null);
            cliente.mesaId = mesa;
            cliente.get("/api/v1/public/mesas/" + qr);
            cliente.tokenCliente = cliente.post("/api/v1/public/mesas/" + qr + "/sesiones", null, 201).get("tokenSesion").asText();
            clientes.add(cliente);
        }
    }

    private Dispositivo personal(String rol) throws Exception {
        Long rolId = jdbc.queryForObject("SELECT id FROM rol WHERE codigo=?", Long.class, rol);
        Long id = insertar("INSERT INTO usuario(local_id,rol_id,nombre,pin_hash,debe_cambiar_credencial,contrasena_provisoria) "
                + "VALUES(?,?,?, ?,false,false)", local, rolId, "Carga " + rol, claves.hashear("123456"));
        String token = tokens.emitir(new DatosDeSesion(id, rol, local, false), Duration.ofHours(1));
        if ("COCINA".equals(rol)) tokenCocina = token;
        var dispositivo = new Dispositivo(token);
        dispositivo.get("/api/v1/ping");
        return dispositivo;
    }

    private Long insertar(String sql, Object... valores) {
        var llave = new GeneratedKeyHolder();
        jdbc.update(conexion -> {
            var sentencia = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < valores.length; i++) sentencia.setObject(i + 1, valores[i]);
            return sentencia;
        }, llave);
        return llave.getKey().longValue();
    }

    private void aprobarYMedir(Dispositivo caja, Long id) throws Exception {
        caja.post("/api/v1/caja/pedidos/" + id + "/aprobar", null, 204);
        JsonNode evento = kds.evento(id);
        double milisegundos = Duration.between(Instant.parse(evento.get("ocurridoEn").asText()), Instant.now()).toNanos() / 1_000_000.0;
        latencias.add(milisegundos);
    }

    private static double p95(List<Double> muestras) {
        var ordenadas = muestras.stream().sorted().toList();
        return ordenadas.get((int) Math.ceil(ordenadas.size() * .95) - 1);
    }

    private static void esperarHasta(long plazo) throws InterruptedException {
        long faltante = plazo - System.nanoTime();
        if (faltante > 0) TimeUnit.NANOSECONDS.sleep(faltante);
    }

    @AfterEach
    void limpiar() throws Exception {
        if (kds != null) kds.close();
        if (local == null) return;
        for (String tabla : List.of("registro_auditoria", "ticket", "item_pedido", "pedido", "cliente_sesion", "aviso",
                "cuenta", "mesa", "plato", "categoria_menu", "usuario")) {
            jdbc.update("DELETE FROM " + tabla + " WHERE local_id=?", local);
        }
        jdbc.update("DELETE FROM local WHERE id=?", local);
    }

    private class Dispositivo {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(10)).build();
        final URI base = URI.create("http://localhost:" + puerto);
        String tokenCliente;
        Long mesaId;
        Dispositivo(String token) {
            if (token != null) {
                var cookie = new HttpCookie("chitgo_sesion", token); cookie.setPath("/"); cookie.setVersion(0);
                cookies.getCookieStore().add(base, cookie);
            }
        }
        JsonNode get(String ruta) throws Exception { return solicitar("GET", ruta, null, 200); }
        JsonNode post(String ruta, Object cuerpo, int esperado) throws Exception { return solicitar("POST", ruta, cuerpo, esperado); }
        Long pedir() throws Exception {
            Long id = post("/api/v1/public/pedidos", Map.of("uuidCliente", UUID.randomUUID().toString(),
                    "items", List.of(Map.of("platoId", plato, "cantidad", 1, "observacion", "mesa " + mesaId))), 201).get("id").asLong();
            assertThat(jdbc.queryForObject("SELECT c.mesa_id FROM pedido p JOIN cuenta c ON c.id=p.cuenta_id WHERE p.id=?", Long.class, id))
                    .isEqualTo(mesaId);
            return id;
        }
        JsonNode solicitar(String metodo, String ruta, Object cuerpo, int esperado) throws Exception {
            var solicitud = HttpRequest.newBuilder(base.resolve(ruta)).timeout(Duration.ofSeconds(20));
            if (tokenCliente != null) solicitud.header("X-Session-Token", tokenCliente);
            if (!"GET".equals(metodo)) {
                String csrf = cookies.getCookieStore().getCookies().stream().filter(c -> "XSRF-TOKEN".equals(c.getName()))
                        .findFirst().orElseThrow().getValue();
                solicitud.header("X-XSRF-TOKEN", csrf).header("Content-Type", "application/json");
            }
            solicitud.method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)));
            var respuesta = http.send(solicitud.build(), HttpResponse.BodyHandlers.ofString());
            assertThat(respuesta.statusCode()).as(metodo + " " + ruta + ": " + respuesta.body()).isEqualTo(esperado);
            return respuesta.body().isEmpty() ? null : json.readTree(respuesta.body());
        }
    }

    private class Stomp implements WebSocket.Listener, AutoCloseable {
        final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        final StringBuilder parcial = new StringBuilder();
        final String token;
        WebSocket socket;
        Stomp(String token) { this.token = token; }
        void conectar() throws Exception {
            socket = HttpClient.newHttpClient().newWebSocketBuilder().header("Cookie", "chitgo_sesion=" + token)
                    .buildAsync(URI.create("ws://localhost:" + puerto + "/ws"), this).get(10, TimeUnit.SECONDS);
            enviar("CONNECT\naccept-version:1.2\nhost:localhost\nheart-beat:0,0\n\n\0");
            assertThat(frames.poll(10, TimeUnit.SECONDS)).startsWith("CONNECTED");
        }
        void enviar(String texto) { socket.sendText(texto, true).join(); }
        @Override public void onOpen(WebSocket socket) { socket.request(1); }
        @Override public CompletionStage<?> onText(WebSocket socket, CharSequence texto, boolean ultimo) {
            parcial.append(texto);
            int fin;
            while ((fin = parcial.indexOf("\0")) >= 0) {
                String frame = parcial.substring(0, fin).stripLeading(); parcial.delete(0, fin + 1);
                if (!frame.isBlank()) frames.add(frame);
            }
            socket.request(1); return null;
        }
        JsonNode evento(Long pedidoId) throws Exception {
            long hasta = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < hasta) {
                String frame = frames.poll(1, TimeUnit.SECONDS);
                if (frame == null) continue;
                assertThat(frame).doesNotStartWith("ERROR");
                if (frame.startsWith("MESSAGE")) {
                    var evento = json.readTree(frame.substring(frame.indexOf("\n\n") + 2));
                    if (evento.get("pedidoId").asLong() == pedidoId) return evento;
                }
            }
            throw new AssertionError("No llegó el pedido " + pedidoId + " al KDS");
        }
        @Override public void close() {
            if (socket != null) socket.abort();
        }
    }
}
