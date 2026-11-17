package com.chitgo.usuarios.infraestructura.web;

import static com.chitgo.pruebas.CsrfReal.csrfReal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.chitgo.compartido.dominio.ReglaDeNegocioException;
import com.chitgo.compartido.dominio.RelojDePrueba;
import com.chitgo.compartido.dominio.SolicitudInvalidaException;
import com.chitgo.compartido.infraestructura.websocket.AutenticacionStompInterceptor;
import com.chitgo.usuarios.aplicacion.SuperadminOperativoService;
import com.chitgo.usuarios.aplicacion.dto.DatosDeAltaDeSuperadmin;
import com.chitgo.usuarios.dominio.DatosDeSesion;
import com.chitgo.usuarios.dominio.Usuario;
import com.chitgo.usuarios.dominio.puertos.CodificadorDeClaves;
import com.chitgo.usuarios.dominio.puertos.EmisorDeTokens;
import com.chitgo.usuarios.dominio.puertos.PermisosPorRol;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeSesion;

/** Prototipo de plataforma: autenticación real, CSRF real, transacciones y MySQL. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@DirtiesContext
class SuperadminTest {
    private static final String BASE = "/api/v1/superadmin";
    private static final String INICIAL = "ProvisoriaSuper2026!";
    private static final String NUEVA = "NuevaClaveSuper2026!";
    private static final String GERENTE = "dueno@laesquina.test";

    @TestConfiguration
    static class ConRelojDePrueba {
        @Bean @Primary
        RelojDePrueba relojDePrueba() { return new RelojDePrueba(Instant.now()); }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired SuperadminOperativoService operadores;
    @Autowired CodificadorDeClaves claves;
    @Autowired PermisosPorRol permisos;
    @Autowired EmisorDeTokens tokens;
    @Autowired AutenticacionStompInterceptor stomp;
    @Autowired RelojDePrueba reloj;
    private Long ultimoUsuario, ultimoLocal, ultimaAuditoria, operadorId;
    private String email;
    private List<Map<String, Object>> originales;

    @BeforeEach
    void prepararOperador() {
        ultimoUsuario = maximo("usuario"); ultimoLocal = maximo("local"); ultimaAuditoria = maximo("registro_auditoria");
        originales = jdbc.queryForList("SELECT id, intentos_fallidos, bloqueado_hasta, activo, sesion_valida_desde FROM usuario");
        email = "super-" + UUID.randomUUID().toString().substring(0, 8) + "@prototipo.test";
        operadorId = operadores.crear(new DatosDeAltaDeSuperadmin("Operador de prueba", email, INICIAL)).id();
    }

    @AfterEach
    void restaurar() {
        jdbc.update("DELETE FROM registro_auditoria WHERE id > ?", ultimaAuditoria);
        jdbc.update("DELETE FROM empleado WHERE local_id > ?", ultimoLocal);
        jdbc.update("DELETE FROM usuario WHERE local_id > ?", ultimoLocal);
        jdbc.update("DELETE FROM local WHERE id > ?", ultimoLocal);
        jdbc.update("DELETE FROM usuario WHERE id > ?", ultimoUsuario);
        for (var fila : originales) jdbc.update("""
                UPDATE usuario SET intentos_fallidos=?, bloqueado_hasta=?, activo=?, sesion_valida_desde=? WHERE id=?
                """, fila.get("intentos_fallidos"), fila.get("bloqueado_hasta"), fila.get("activo"),
                fila.get("sesion_valida_desde"), fila.get("id"));
    }

    @Test
    void elOperadorNaceSinLocalConHashYCambioObligatorioYNoPuedeOperarHastaCambiar() throws Exception {
        var usuario = jdbc.queryForMap("SELECT local_id, hash_password, debe_cambiar_credencial FROM usuario WHERE id=?", operadorId);
        assertThat(usuario.get("local_id")).isNull();
        assertThat(claves.verificar(INICIAL, (String) usuario.get("hash_password"))).isTrue();
        Cookie provisional = cookie(login(email, INICIAL).andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("SUPERADMIN"))
                .andExpect(jsonPath("$.local").isEmpty())
                .andExpect(jsonPath("$.debeCambiarCredencial").value(true)));
        assertThat(provisional.isHttpOnly()).isTrue();
        assertThat(provisional.getMaxAge()).isEqualTo(600);
        enviar(BASE + "/locales", alta(), provisional).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CREDENCIAL_PROVISORIA"));
        enviar(BASE + "/gerentes/desbloquear", Map.of("email", GERENTE), provisional)
                .andExpect(status().isForbidden());
        Cookie propia = cambiar(provisional, NUEVA);
        mvc.perform(get("/api/v1/auth/sesion").cookie(propia)).andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("SUPERADMIN"))
                .andExpect(jsonPath("$.debeCambiarCredencial").value(false));
        login(email, INICIAL).andExpect(status().isUnauthorized()).andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        login(email, NUEVA).andExpect(status().isOk());
    }

    @Test
    void creaLocalConGerenteFuncionalYElAutorDeAuditoriaEsElSuperadmin() throws Exception {
        Cookie superadmin = sesion();
        Map<String, Object> datos = new java.util.LinkedHashMap<>(alta());
        datos.put("usuarioId", 1); // Campo ajeno al DTO: jamás determina el autor.
        JsonNode creado = arbol(enviar(BASE + "/locales", datos, superadmin).andExpect(status().isCreated()));
        long local = creado.get("localId").asLong();
        long gerente = creado.get("gerenteId").asLong();
        assertThat(jdbc.queryForObject("SELECT propietario_usuario_id FROM local WHERE id=?", Long.class, local)).isEqualTo(gerente);
        var registro = jdbc.queryForMap("SELECT usuario_id,local_id,valor_nuevo FROM registro_auditoria WHERE id>? AND accion='CREAR_LOCAL'", ultimaAuditoria);
        assertThat(registro.get("usuario_id")).isEqualTo(operadorId);
        assertThat(registro.get("local_id")).isEqualTo(local);
        assertThat(registro.get("valor_nuevo").toString()).contains("superadmin").doesNotContain(INICIAL, "hash_password", "tokenStaff");
        Cookie gerenteProvisorio = cookie(login("gerente@super-prototipo.test", INICIAL).andExpect(status().isOk()));
        Cookie gerentePropio = cambiar(gerenteProvisorio, "NuevaClaveGerente2026!");
        enviar("/api/v1/gerente/admins", Map.of("nombre", "Admin de prueba", "email", "admin@super-prototipo.test",
                "password", INICIAL), gerentePropio).andExpect(status().isCreated());
        Cookie admin = cambiar(cookie(login("admin@super-prototipo.test", INICIAL).andExpect(status().isOk())), "NuevaClaveAdmin2026!");
        mvc.perform(get("/api/v1/admin/mesas").cookie(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(0));
        Long mesaAjena = jdbc.queryForObject("SELECT MIN(id) FROM mesa", Long.class);
        mvc.perform(get("/api/v1/admin/mesas/" + mesaAjena).cookie(admin)).andExpect(status().isNotFound());
    }

    @Test
    void desbloqueaGerentesDeDosLocalesConAutorRealYSinDuplicarAuditoria() throws Exception {
        Cookie superadmin = sesion();
        for (String gerente : List.of(GERENTE, "dueno@elpuerto.test")) {
            jdbc.update("UPDATE usuario SET intentos_fallidos=5,bloqueado_hasta='2038-01-01 00:00:00' WHERE email=?", gerente);
            enviar(BASE + "/gerentes/desbloquear", Map.of("email", gerente), superadmin).andExpect(status().isOk())
                    .andExpect(jsonPath("$.resultado").value("DESBLOQUEADO"));
            assertThat(jdbc.queryForObject("SELECT intentos_fallidos FROM usuario WHERE email=?", Integer.class, gerente)).isZero();
            enviar(BASE + "/gerentes/desbloquear", Map.of("email", gerente), superadmin).andExpect(status().isOk())
                    .andExpect(jsonPath("$.resultado").value("NO_ESTABA_BLOQUEADO"));
        }
        var registros = jdbc.queryForList("SELECT usuario_id,local_id,entidad_id,valor_nuevo FROM registro_auditoria WHERE id>?", ultimaAuditoria);
        assertThat(registros).hasSize(2);
        for (var registro : registros) {
            assertThat(registro.get("usuario_id")).isEqualTo(operadorId);
            assertThat(registro.get("entidad_id")).isNotEqualTo(operadorId);
            assertThat(registro.get("local_id")).isNotNull();
            assertThat(registro.get("valor_nuevo").toString()).contains("superadmin");
        }
    }

    @Test
    void dosAltasConcurrentesConElMismoEmailSoloCreanUnLocalYUnGerente() throws Exception {
        Cookie actor = sesion();
        try (var hilos = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var inicio = new java.util.concurrent.CountDownLatch(1);
            var resultados = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < 2; i++) resultados.add(hilos.submit(() -> {
                inicio.await();
                return enviar(BASE + "/locales", alta(), actor).andReturn().getResponse().getStatus();
            }));
            inicio.countDown();
            var estados = new java.util.ArrayList<Integer>();
            for (var resultado : resultados) estados.add(resultado.get(15, java.util.concurrent.TimeUnit.SECONDS));
            assertThat(estados).containsExactlyInAnyOrder(201, 409);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM local WHERE id>?", Integer.class, ultimoLocal)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE email='gerente@super-prototipo.test'", Integer.class))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM registro_auditoria WHERE id>?", Integer.class, ultimaAuditoria)).isEqualTo(1);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "GERENTE_DUENO", "MOZO", "CAJA", "COCINA"})
    void losRolesExistentesNoObtienenPermisosDePlataforma(String rol) throws Exception {
        var usuario = jdbc.queryForMap("SELECT u.id,u.local_id FROM usuario u JOIN rol r ON r.id=u.rol_id WHERE r.codigo=? ORDER BY u.id LIMIT 1", rol);
        String jwt = tokens.emitir(new DatosDeSesion(((Number) usuario.get("id")).longValue(), rol,
                (Long) usuario.get("local_id"), false), Duration.ofHours(1));
        Cookie actor = new Cookie(CookieDeSesion.NOMBRE, jwt);
        enviar(BASE + "/locales", alta(), actor).andExpect(status().isForbidden());
        enviar(BASE + "/gerentes/desbloquear", Map.of("email", GERENTE), actor).andExpect(status().isForbidden());
        assertThat(maximo("local")).isEqualTo(ultimoLocal);
    }

    @Test
    void superadminNoHeredaOperacionNiReportesNiAuditoriaDeLosLocales() throws Exception {
        Cookie actor = sesion();
        assertThat(permisos.de("SUPERADMIN")).containsExactlyInAnyOrder("local.crear", "gerente.desbloquear");
        for (String ruta : List.of("/api/v1/admin/empleados", "/api/v1/admin/platos", "/api/v1/admin/mesas",
                "/api/v1/admin/pantalla", "/api/v1/admin/auditoria", "/api/v1/gerente/admins",
                "/api/v1/caja/pedidos", "/api/v1/mozo/mesas", "/api/v1/cocina/pedidos",
                "/api/v1/gerente/auditoria", "/api/v1/admin/reportes/ventas?desde=2026-01-01&hasta=2026-12-31")) {
            mvc.perform(get(ruta).cookie(actor)).andExpect(status().isForbidden());
        }
        mvc.perform(get(BASE + "/locales").cookie(actor)).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void exigeSesionYCsrfSinCrearDatos() throws Exception {
        enviar(BASE + "/locales", alta(), null).andExpect(status().isUnauthorized());
        enviar(BASE + "/gerentes/desbloquear", Map.of("email", GERENTE), null).andExpect(status().isUnauthorized());
        Cookie actor = sesion();
        for (String ruta : List.of(BASE + "/locales", BASE + "/gerentes/desbloquear")) {
            mvc.perform(post(ruta).cookie(actor).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
        assertThat(maximo("local")).isEqualTo(ultimoLocal);
        assertThat(maximo("registro_auditoria")).isEqualTo(ultimaAuditoria);
    }

    @Test
    void datosInvalidosYEmailDuplicadoNoDejanUnLocalNiUnGerenteParciales() throws Exception {
        Cookie actor = sesion();
        for (var invalido : List.of(Map.of(), Map.of("nombreLocal", "Local", "email", "invalido"))) {
            enviar(BASE + "/locales", invalido, actor).andExpect(status().isBadRequest());
        }
        var repetido = new java.util.LinkedHashMap<>(alta()); repetido.put("email", " DUENO@LaEsquina.test ");
        long usuariosAntes = maximo("usuario");
        enviar(BASE + "/locales", repetido, actor).andExpect(status().isConflict());
        assertThat(maximo("local")).isEqualTo(ultimoLocal);
        assertThat(maximo("usuario")).isEqualTo(usuariosAntes);
        assertThat(maximo("registro_auditoria")).isEqualTo(ultimaAuditoria);
    }

    @Test
    void desbloqueoValidaEmailYRechazaOtrosRolesOUsuariosSinLocal() throws Exception {
        Cookie actor = sesion();
        for (String invalido : List.of("", "malformado", "a".repeat(161))) {
            enviar(BASE + "/gerentes/desbloquear", Map.of("email", invalido), actor).andExpect(status().isBadRequest());
        }
        for (String ajeno : List.of("admin@laesquina.test", email, "nadie@prototipo.test")) {
            enviar(BASE + "/gerentes/desbloquear", Map.of("email", ajeno), actor).andExpect(status().isNotFound());
        }
        jdbc.update("""
                INSERT INTO usuario(rol_id,nombre,email,hash_password,debe_cambiar_credencial)
                SELECT rol_id,'Gerente sin local','sinlocal@prototipo.test',hash_password,FALSE FROM usuario WHERE email=?
                """, GERENTE);
        enviar(BASE + "/gerentes/desbloquear", Map.of("email", "sinlocal@prototipo.test"), actor)
                .andExpect(status().isNotFound());
    }

    @Test
    void unGerenteInactivoNoSeReactivaAlDesbloquearlo() throws Exception {
        Cookie actor = sesion();
        jdbc.update("UPDATE usuario SET activo=FALSE,intentos_fallidos=5,bloqueado_hasta='2038-01-01 00:00:00' WHERE email=?", GERENTE);
        enviar(BASE + "/gerentes/desbloquear", Map.of("email", GERENTE), actor).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT activo FROM usuario WHERE email=?", Boolean.class, GERENTE)).isFalse();
        assertThat(jdbc.queryForObject("SELECT intentos_fallidos FROM usuario WHERE email=?", Integer.class, GERENTE)).isEqualTo(5);
    }

    @Test
    void rnf07BloqueaAlSuperadminYSeRecuperaSoloPorElComandoOperativo() throws Exception {
        for (int i = 0; i < 5; i++) login(email, "IncorrectaSuper2026!").andExpect(status().isUnauthorized());
        login(email, INICIAL).andExpect(status().isUnauthorized()).andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        assertThat(jdbc.queryForObject("SELECT intentos_fallidos FROM usuario WHERE id=?", Integer.class, operadorId)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT UNIX_TIMESTAMP(bloqueado_hasta) FROM usuario WHERE id=?", Long.class, operadorId))
                .isEqualTo(Usuario.BLOQUEO_SIN_VENCIMIENTO.getEpochSecond());
        assertThat(operadores.desbloquear(email)).isTrue();
        assertThat(operadores.desbloquear(email)).isFalse();
        login(email, INICIAL).andExpect(status().isOk()).andExpect(jsonPath("$.debeCambiarCredencial").value(true));
    }

    @Test
    void bajaDelSuperadminRechazaNuevoLoginYSuSesionAbierta() throws Exception {
        Cookie actor = sesion();
        jdbc.update("UPDATE usuario SET activo=FALSE WHERE id=?", operadorId);
        login(email, NUEVA).andExpect(status().isUnauthorized()).andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        enviar(BASE + "/locales", alta(), actor).andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> operadores.desbloquear(email)).isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    void elAprovisionamientoNoSobrescribeUsuariosNiAdmiteDatosInvalidos() {
        assertThatThrownBy(() -> operadores.crear(new DatosDeAltaDeSuperadmin("Otro", email.toUpperCase(), INICIAL)))
                .isInstanceOf(ReglaDeNegocioException.class);
        assertThatThrownBy(() -> operadores.crear(new DatosDeAltaDeSuperadmin("", "mal", "corta")))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> operadores.desbloquear(GERENTE)).isInstanceOf(ReglaDeNegocioException.class);
        assertThatThrownBy(() -> operadores.desbloquear(null)).isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void vencimientoYLogoutConservanElCorteDeSesion() throws Exception {
        Cookie actor = sesion();
        reloj.avanzar(Duration.ofSeconds(2));
        enviar("/api/v1/auth/logout", null, actor).andExpect(status().isNoContent());
        enviar(BASE + "/locales", alta(), actor).andExpect(status().isUnauthorized());
        Cookie nueva = cookie(login(email, NUEVA).andExpect(status().isOk()));
        reloj.avanzar(Duration.ofMinutes(11));
        enviar(BASE + "/locales", alta(), nueva).andExpect(status().isUnauthorized());
    }

    @Test
    void elJwtDeSuperadminNoObtieneTopicsDeLocalesNiPuedePublicarPorStomp() throws Exception {
        Cookie actor = sesion();
        var conectar = StompHeaderAccessor.create(StompCommand.CONNECT);
        conectar.setSessionAttributes(Map.of("chitgo.jwt", actor.getValue())); conectar.setLeaveMutable(true);
        var conectado = stomp.preSend(MessageBuilder.createMessage(new byte[0], conectar.getMessageHeaders()), null);
        var principal = MessageHeaderAccessor.getAccessor(conectado, StompHeaderAccessor.class).getUser();
        for (String destino : List.of("/topic/local/1/alertas", "/topic/local/2/cocina/cola", "/topic/local/1/pantalla", "/user/queue/pedido")) {
            var suscribir = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
            suscribir.setUser(principal); suscribir.setDestination(destino); suscribir.setLeaveMutable(true);
            assertThatThrownBy(() -> stomp.preSend(MessageBuilder.createMessage(new byte[0], suscribir.getMessageHeaders()), null))
                    .isInstanceOf(MessagingException.class);
        }
        var enviar = StompHeaderAccessor.create(StompCommand.SEND); enviar.setUser(principal); enviar.setLeaveMutable(true);
        assertThatThrownBy(() -> stomp.preSend(MessageBuilder.createMessage(new byte[0], enviar.getMessageHeaders()), null))
                .isInstanceOf(MessagingException.class);
    }

    private Map<String, String> alta() {
        return Map.of("nombreLocal", "Local SuperAdmin", "direccion", "Calle de prueba 1", "nombreGerente", "Gerente de prueba",
                "email", "gerente@super-prototipo.test", "password", INICIAL);
    }

    private Cookie sesion() throws Exception { return cambiar(cookie(login(email, INICIAL).andExpect(status().isOk())), NUEVA); }

    private Cookie cambiar(Cookie actor, String nueva) throws Exception {
        return cookie(enviar("/api/v1/auth/credencial", Map.of("password", nueva, "passwordRepetida", nueva), actor)
                .andExpect(status().isOk()).andExpect(jsonPath("$.debeCambiarCredencial").value(false)));
    }

    private ResultActions login(String usuario, String password) throws Exception {
        return enviar("/api/v1/auth/admin/login", Map.of("email", usuario, "password", password), null);
    }

    private ResultActions enviar(String ruta, Object cuerpo, Cookie actor) throws Exception {
        MockHttpServletRequestBuilder request = post(ruta).with(csrfReal(mvc));
        if (actor != null) request.cookie(actor);
        if (cuerpo != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo));
        return mvc.perform(request);
    }

    private Cookie cookie(ResultActions resultado) throws Exception {
        return resultado.andReturn().getResponse().getCookie(CookieDeSesion.NOMBRE);
    }

    private JsonNode arbol(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    private Long maximo(String tabla) { return jdbc.queryForObject("SELECT COALESCE(MAX(id),0) FROM " + tabla, Long.class); }
}
