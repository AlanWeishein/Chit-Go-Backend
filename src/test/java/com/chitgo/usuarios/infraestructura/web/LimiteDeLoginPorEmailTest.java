package com.chitgo.usuarios.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import com.chitgo.compartido.dominio.RelojDePrueba;
import com.chitgo.usuarios.infraestructura.seguridad.ClaveDeEmailParaLimite;
import com.chitgo.usuarios.infraestructura.seguridad.CookieDeSesion;

/** Protección activa, CSRF real y MySQL; cada test parte con cupos propios, sin sleeps. */
@SpringBootTest(properties = "chitgo.seguridad.login-email.maximo-de-claves=32")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class LimiteDeLoginPorEmailTest {
    private static final String LOGIN = "/api/v1/auth/admin/login";
    private static final String ADMIN = "admin@laesquina.test";
    private static final String DUENO = "dueno@laesquina.test";
    private static final String CORRECTA = "Chitgo2026!";
    private static final String INCORRECTA = "contrasenaIncorrecta";
    private static final String ORIGEN = "192.0.2.10";

    @TestConfiguration
    static class ConRelojDePrueba {
        @Bean
        @Primary
        RelojDePrueba relojDePrueba() { return new RelojDePrueba(Instant.now()); }
    }

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private RelojDePrueba reloj;
    @Autowired private ClaveDeEmailParaLimite claves;
    private Cookie xsrf;

    @BeforeEach
    void prepararCsrf() throws Exception {
        xsrf = mvc.perform(get("/api/v1/ping")).andExpect(status().isOk())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();
    }

    @AfterEach
    void restaurarSeed() {
        jdbc.update("UPDATE usuario SET intentos_fallidos = 0, bloqueado_hasta = NULL, activo = TRUE");
    }

    @ParameterizedTest
    @ValueSource(strings = {ADMIN, DUENO})
    void loginLegitimoConservaCookieYSesion(String email) throws Exception {
        MvcResult resultado = ingresar(email, CORRECTA, ORIGEN).andExpect(status().isOk()).andReturn();
        Cookie sesion = resultado.getResponse().getCookie(CookieDeSesion.NOMBRE);
        assertThat(sesion).isNotNull();
        assertThat(sesion.isHttpOnly()).isTrue();
        assertThat(sesion.getSecure()).isFalse(); // perfil dev
        assertThat(resultado.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("SameSite=Strict");
        mvc.perform(get("/api/v1/auth/sesion").cookie(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value(email.equals(ADMIN) ? "ADMIN" : "GERENTE_DUENO"));
    }

    @ParameterizedTest
    @ValueSource(strings = {ADMIN, DUENO})
    void laRafagaDesdeCeroNoAlcanzaElBloqueoYLaCorrectaTambienRespetaElCupo(String email) throws Exception {
        assertThat(intentos(email)).isZero();
        for (int i = 1; i <= 5; i++) {
            if (i <= 3) {
                ingresar(email, INCORRECTA, ORIGEN).andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.detail").value("Email o contraseña incorrectos"))
                        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
            } else limitada(ingresar(email, INCORRECTA, ORIGEN));
            assertThat(intentos(email)).isEqualTo(Math.min(i, 3));
        }
        limitada(ingresar(email, CORRECTA, ORIGEN));
        assertThat(intentos(email)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT bloqueado_hasta FROM usuario WHERE email = ?", Object.class, email))
                .isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {ADMIN, DUENO})
    void retryAfterSeRedondeaYElLoginSeRecuperaAlVencerLaVentana(String email) throws Exception {
        agotar(email, ORIGEN);
        reloj.avanzar(Duration.ofMillis(59_500));
        limitada(ingresar(email, CORRECTA, ORIGEN)).andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"));
        assertThat(intentos(email)).isEqualTo(3);
        reloj.avanzar(Duration.ofMillis(500));
        ingresar(email, CORRECTA, ORIGEN).andExpect(status().isOk());
        assertThat(intentos(email)).isZero();
    }

    @Test
    void otroOrigenPuedeIngresarSinVaciarElCupoDelOrigenAgotado() throws Exception {
        agotar(ADMIN, ORIGEN);
        ingresar(ADMIN, CORRECTA, "192.0.2.11").andExpect(status().isOk());
        assertThat(intentos(ADMIN)).isZero();
        limitada(ingresar(ADMIN, CORRECTA, ORIGEN));
        assertThat(intentos(ADMIN)).isZero();
    }

    @Test
    void treintaSolicitudesDeCuentasDistintasCompartenNatSinUnLimiteGlobalPorIp() throws Exception {
        agotar(ADMIN, ORIGEN);
        // El Gerente conserva su propio cupo pese a que Admin agotó el suyo en la misma red.
        for (int i = 0; i < 3; i++) ingresar(DUENO, CORRECTA, ORIGEN).andExpect(status().isOk());
        for (int cuenta = 0; cuenta < 8; cuenta++) agotar("nadie" + cuenta + "@ejemplo.test", ORIGEN);
        assertThat(intentos(ADMIN)).isEqualTo(3);
        assertThat(intentos(DUENO)).isZero();
        limitada(ingresar(ADMIN, INCORRECTA, ORIGEN));
    }

    @Test
    void laRespuestaNoDistingueUnaCuentaExistenteDeUnaInexistente() throws Exception {
        for (int i = 0; i < 5; i++) {
            MvcResult existente = ingresar(ADMIN, INCORRECTA, ORIGEN).andReturn();
            MvcResult inexistente = ingresar("nadie@ejemplo.test", INCORRECTA, ORIGEN).andReturn();
            assertThat(existente.getResponse().getStatus()).isEqualTo(i < 3 ? 401 : 429);
            assertThat(inexistente.getResponse().getStatus()).isEqualTo(existente.getResponse().getStatus());
            assertThat(json.readTree(inexistente.getResponse().getContentAsString()))
                    .isEqualTo(json.readTree(existente.getResponse().getContentAsString()));
            assertThat(inexistente.getResponse().getHeader(HttpHeaders.RETRY_AFTER))
                    .isEqualTo(existente.getResponse().getHeader(HttpHeaders.RETRY_AFTER));
            assertThat(inexistente.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        }
    }

    @Test
    void mayusculasAcentosCombinadosYCaracteresDeAnchoCompletoNoCreanCuposNuevos() throws Exception {
        String[] variantes = {" Admin@LaEsquina.test ", "ádmin@laesquina.test", "a\u0301dmin@laesquina.test",
                "ａｄｍｉｎ@laesquina.test", "ADMIN@laesquina.test"};
        for (int i = 0; i < variantes.length; i++) {
            String variante = variantes[i];
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE email = ?", Integer.class,
                    variante.strip())).isEqualTo(1);
            assertThat(claves.de(variante)).isEqualTo(claves.de(ADMIN));
            if (i < 3) ingresar(variante, INCORRECTA, ORIGEN).andExpect(status().isUnauthorized());
            else limitada(ingresar(variante, INCORRECTA, ORIGEN));
        }
        assertThat(intentos(ADMIN)).isEqualTo(3);
    }

    @Test
    void laEquivalenciaTambienSeAplicaSiElEmailNoExiste() throws Exception {
        for (String variante : new String[] {"nadie@ejemplo.test", "NADIE@ejemplo.test", "nádie@ejemplo.test"}) {
            assertThat(claves.de(variante)).isEqualTo(claves.de("nadie@ejemplo.test"));
            ingresar(variante, INCORRECTA, ORIGEN).andExpect(status().isUnauthorized());
        }
        limitada(ingresar("na\u0301die@ejemplo.test", INCORRECTA, ORIGEN));
        assertThat(intentos(ADMIN)).isZero();
    }

    @Test
    void losHeadersDeProxyNoCambianElOrigen() throws Exception {
        for (int i = 0; i < 4; i++) {
            ResultActions respuesta = mvc.perform(pedido(ADMIN, INCORRECTA, ORIGEN)
                    .header("X-Forwarded-For", "198.51.100." + i)
                    .header("X-Real-IP", "198.51.100." + i)
                    .header("Forwarded", "for=198.51.100." + i));
            if (i < 3) respuesta.andExpect(status().isUnauthorized());
            else limitada(respuesta);
        }
        assertThat(intentos(ADMIN)).isEqualTo(3);
    }

    @Test
    void csrfYValidacionActuanAntesDelLimiterYNoConsumenCupo() throws Exception {
        for (int i = 0; i < 4; i++) {
            mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(new Pedido(ADMIN, CORRECTA))))
                    .andExpect(status().isForbidden());
            ingresar(ADMIN, "corta", ORIGEN).andExpect(status().isBadRequest());
            mvc.perform(post(LOGIN).cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                    .contentType(MediaType.APPLICATION_JSON).content("{invalido"))
                    .andExpect(status().isBadRequest());
        }
        assertThat(intentos(ADMIN)).isZero();
        agotar(ADMIN, ORIGEN);
        limitada(ingresar(ADMIN, CORRECTA, ORIGEN));
        ingresar(ADMIN, "corta", ORIGEN).andExpect(status().isBadRequest());
    }

    @Test
    void unIngresoExitosoTambienConsumeCupoSinBloquearLaCuenta() throws Exception {
        for (int i = 0; i < 3; i++) ingresar(ADMIN, CORRECTA, ORIGEN).andExpect(status().isOk());
        limitada(ingresar(ADMIN, CORRECTA, ORIGEN));
        assertThat(intentos(ADMIN)).isZero();
    }

    @Test
    void memoriaLlenaDa429SinSesionNiIntentosYLuegoSeRecupera() throws Exception {
        for (int i = 0; i < 32; i++) {
            ingresar("memoria" + i + "@ejemplo.test", INCORRECTA, ORIGEN)
                    .andExpect(status().isUnauthorized());
        }
        limitada(ingresar(ADMIN, CORRECTA, ORIGEN)).andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"));
        assertThat(intentos(ADMIN)).isZero();
        // No se expulsan las claves activas para hacer lugar. Conservan sus lugares restantes.
        ingresar("memoria0@ejemplo.test", INCORRECTA, ORIGEN).andExpect(status().isUnauthorized());
        reloj.avanzar(Duration.ofSeconds(60));
        ingresar(ADMIN, CORRECTA, ORIGEN).andExpect(status().isOk());
        assertThat(intentos(ADMIN)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {ADMIN, DUENO})
    void doceRequestsConcurrentesSoloAutenticanTresVeces(String email) throws Exception {
        try (var executor = Executors.newFixedThreadPool(12)) {
            var inicio = new CountDownLatch(1);
            var resultados = new ArrayList<Future<MvcResult>>();
            for (int i = 0; i < 12; i++) resultados.add(executor.submit(() -> {
                inicio.await();
                return ingresar(email, INCORRECTA, ORIGEN).andReturn();
            }));
            inicio.countDown();
            var estados = new ArrayList<Integer>();
            for (var resultado : resultados) {
                var respuesta = resultado.get(20, TimeUnit.SECONDS).getResponse();
                estados.add(respuesta.getStatus());
                assertThat(respuesta.getHeader(HttpHeaders.SET_COOKIE)).isNull();
            }
            assertThat(estados).containsOnly(401, 429);
            assertThat(estados.stream().filter(e -> e == 401).count()).isEqualTo(3);
            assertThat(estados.stream().filter(e -> e == 429).count()).isEqualTo(9);
            assertThat(intentos(email)).isEqualTo(3);
        }
    }

    private ResultActions limitada(ResultActions respuesta) throws Exception {
        return respuesta.andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail").value("Demasiadas solicitudes de ingreso. Intentá de nuevo más tarde."))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    private void agotar(String email, String origen) throws Exception {
        for (int i = 0; i < 3; i++) ingresar(email, INCORRECTA, origen).andExpect(status().isUnauthorized());
    }

    private ResultActions ingresar(String email, String contrasena, String origen) throws Exception {
        return mvc.perform(pedido(email, contrasena, origen));
    }

    private MockHttpServletRequestBuilder pedido(String email, String contrasena, String origen) throws Exception {
        return post(LOGIN).with(request -> { request.setRemoteAddr(origen); return request; })
                .cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new Pedido(email, contrasena)));
    }

    private int intentos(String email) {
        return jdbc.queryForObject("SELECT intentos_fallidos FROM usuario WHERE email = ?", Integer.class, email);
    }

    private record Pedido(String email, String password) { }
}
