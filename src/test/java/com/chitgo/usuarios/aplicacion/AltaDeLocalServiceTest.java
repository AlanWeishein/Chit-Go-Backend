package com.chitgo.usuarios.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

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

import com.chitgo.compartido.dominio.ReglaDeNegocioException;
import com.chitgo.compartido.dominio.SolicitudInvalidaException;
import com.chitgo.usuarios.aplicacion.AltaDeLocalService.ResultadoDelDesbloqueo;
import com.chitgo.usuarios.aplicacion.dto.DatosDeAltaDeLocal;
import com.chitgo.usuarios.aplicacion.dto.LocalCreado;
import com.chitgo.usuarios.dominio.puertos.CodificadorDeClaves;

/**
 * El script del local de HU-16 (tarea 2 de la ST-24), contra el MySQL del Compose y la seed, sin
 * {@code @Transactional}: el Service abre y cierra la suya, como cuando lo llama el comando. Cada test borra el local
 * de prueba, su Gerente/Dueño y su auditoría, y deja la base como la seed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AltaDeLocalServiceTest {

    private static final String CONTRASENA = "Provisoria2026!";
    private static final String EMAIL = "dueno@laprueba.test";

    @Autowired
    private AltaDeLocalService servicio;

    @Autowired
    private CodificadorDeClaves codificador;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    private Long ultimaAuditoria;
    private Long ultimoLocal;
    private Long ultimoUsuario;
    private List<Map<String, Object>> usuariosDeLaSeed;

    @BeforeEach
    void anotarComoEstaLaSeed() {
        ultimaAuditoria = maximo("registro_auditoria");
        ultimoLocal = maximo("local");
        ultimoUsuario = maximo("usuario");
        usuariosDeLaSeed = jdbc.queryForList("SELECT id, intentos_fallidos, bloqueado_hasta FROM usuario");
    }

    @AfterEach
    void dejarLaBaseComoLaSeed() {
        jdbc.update("DELETE FROM registro_auditoria WHERE id > ?", ultimaAuditoria);
        jdbc.update("DELETE FROM local WHERE id > ?", ultimoLocal);
        jdbc.update("DELETE FROM usuario WHERE id > ?", ultimoUsuario);
        for (Map<String, Object> fila : usuariosDeLaSeed) {
            jdbc.update("UPDATE usuario SET intentos_fallidos = ?, bloqueado_hasta = ? WHERE id = ?",
                    fila.get("intentos_fallidos"), fila.get("bloqueado_hasta"), fila.get("id"));
        }
    }

    @Test
    void creaElGerenteSinLocalYDespuesElLocalQueLoApunta() {
        LocalCreado creado = servicio.darDeAlta(datos("  La Prueba  ", "Calle Falsa 123", "Dueño La Prueba",
                " " + EMAIL + " ", CONTRASENA));

        Map<String, Object> gerente = jdbc.queryForMap("""
                SELECT u.local_id, u.nombre, u.email, u.hash_password, u.debe_cambiar_credencial, u.activo, r.codigo
                FROM usuario u JOIN rol r ON r.id = u.rol_id WHERE u.id = ?""", creado.gerenteId());
        assertThat(gerente.get("local_id")).isNull();
        assertThat(gerente.get("nombre")).isEqualTo("Dueño La Prueba");
        assertThat(gerente.get("email")).isEqualTo(EMAIL);
        assertThat(gerente.get("codigo")).isEqualTo("GERENTE_DUENO");
        assertThat(gerente.get("debe_cambiar_credencial")).isEqualTo(true);
        assertThat(gerente.get("activo")).isEqualTo(true);
        assertThat(codificador.verificar(CONTRASENA, (String) gerente.get("hash_password"))).isTrue();

        Map<String, Object> local = jdbc.queryForMap(
                "SELECT propietario_usuario_id, nombre, direccion, token_staff FROM local WHERE id = ?",
                creado.localId());
        assertThat(local.get("propietario_usuario_id")).isEqualTo(creado.gerenteId());
        assertThat(local.get("nombre")).isEqualTo("La Prueba");
        assertThat(local.get("direccion")).isEqualTo("Calle Falsa 123");
        assertThat(local.get("token_staff")).isEqualTo(creado.tokenStaff());
        assertThat(creado.tokenStaff()).matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(creado.tokenStaff()).isNotIn("83f39b25-deac-4fde-80cc-4cfdf8117713",
                "de076a51-1fbd-46f5-98c5-c895ce59a007");
    }

    @Test
    void elAltaQuedaEnLaAuditoriaDelLocalSinLaContrasena() {
        LocalCreado creado = servicio.darDeAlta(datos("La Prueba", null, "Dueño La Prueba", EMAIL, CONTRASENA));

        List<Map<String, Object>> registros = auditoriaNueva();
        assertThat(registros).hasSize(1);
        Map<String, Object> registro = registros.getFirst();
        assertThat(registro.get("local_id")).isEqualTo(creado.localId());
        assertThat(registro.get("usuario_id")).isEqualTo(creado.gerenteId());
        assertThat(registro.get("accion")).isEqualTo("CREAR_LOCAL");
        assertThat(registro.get("entidad")).isEqualTo("LOCAL");
        assertThat(registro.get("entidad_id")).isEqualTo(creado.localId());
        assertThat(registro.get("valor_anterior")).isNull();
        String nuevo = (String) registro.get("valor_nuevo");
        assertThat(nuevo).contains("\"origen\": \"script\"", "La Prueba", EMAIL, "\"direccion\": null");
        String hash = jdbc.queryForObject("SELECT hash_password FROM usuario WHERE id = ?", String.class,
                creado.gerenteId());
        assertThat(nuevo).doesNotContain(CONTRASENA, hash, "password", "hash");
    }

    @Test
    void elGerenteNuevoEntraConLaProvisoriaYTieneQueCambiarla() throws Exception {
        servicio.darDeAlta(datos("La Prueba", null, "Dueño La Prueba", EMAIL, CONTRASENA));

        Cookie xsrf = mvc.perform(get("/api/v1/ping")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        mvc.perform(post("/api/v1/auth/admin/login").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + EMAIL + "\", \"password\": \"" + CONTRASENA + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("GERENTE_DUENO"))
                .andExpect(jsonPath("$.nombre").value("Dueño La Prueba"))
                .andExpect(jsonPath("$.debeCambiarCredencial").value(true));
    }

    @Test
    void elEmailRepetidoCortaSinGuardarNadaAunqueCambienLasMayusculas() {
        for (String email : List.of("dueno@laesquina.test", "ADMIN@laesquina.TEST")) {
            assertThatThrownBy(() -> servicio.darDeAlta(datos("La Prueba", null, "Otro", email, CONTRASENA)))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessage("Ya hay un usuario con ese email");
        }
        assertThat(maximo("usuario")).isEqualTo(ultimoUsuario);
        assertThat(maximo("local")).isEqualTo(ultimoLocal);
        assertThat(auditoriaNueva()).isEmpty();
    }

    @Test
    void losDatosQueNoSirvenCortanSinGuardarNada() {
        assertThatThrownBy(() -> servicio.darDeAlta(datos(" ", null, null, "no-es-un-email", "corta")))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessage("Falta el nombre del local. Falta el nombre del Gerente/Dueño. El email no es válido. "
                        + "La contraseña tiene que tener de 8 a 64 caracteres");
        assertThatThrownBy(() -> servicio.darDeAlta(datos("La Prueba", null, "Dueño", EMAIL, null)))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessage("Falta la contraseña");
        assertThatThrownBy(() -> servicio.darDeAlta(datos("La Prueba", null, "Dueño", "a".repeat(160) + "@x.test",
                CONTRASENA)))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("El email es demasiado largo");
        assertThat(maximo("usuario")).isEqualTo(ultimoUsuario);
        assertThat(maximo("local")).isEqualTo(ultimoLocal);
        assertThat(auditoriaNueva()).isEmpty();
    }

    @Test
    void elDesbloqueoVuelveLosIntentosACeroYQuedaEnLaAuditoria() {
        LocalCreado creado = servicio.darDeAlta(datos("La Prueba", null, "Dueño La Prueba", EMAIL, CONTRASENA));
        jdbc.update("UPDATE usuario SET intentos_fallidos = 5, bloqueado_hasta = '2038-01-01 00:00:00' WHERE id = ?",
                creado.gerenteId());
        Long antes = maximo("registro_auditoria");

        assertThat(servicio.desbloquearGerente(" DUENO@laprueba.test ")).isEqualTo(ResultadoDelDesbloqueo.DESBLOQUEADO);

        Map<String, Object> gerente = jdbc.queryForMap(
                "SELECT intentos_fallidos, bloqueado_hasta FROM usuario WHERE id = ?", creado.gerenteId());
        assertThat(((Number) gerente.get("intentos_fallidos")).intValue()).isZero();
        assertThat(gerente.get("bloqueado_hasta")).isNull();

        List<Map<String, Object>> registros = jdbc.queryForList(
                "SELECT * FROM registro_auditoria WHERE id > ?", antes);
        assertThat(registros).hasSize(1);
        Map<String, Object> registro = registros.getFirst();
        assertThat(registro.get("local_id")).isEqualTo(creado.localId());
        assertThat(registro.get("usuario_id")).isEqualTo(creado.gerenteId());
        assertThat(registro.get("accion")).isEqualTo("DESBLOQUEAR_GERENTE");
        assertThat(registro.get("entidad")).isEqualTo("USUARIO");
        assertThat(registro.get("entidad_id")).isEqualTo(creado.gerenteId());
        assertThat((String) registro.get("valor_anterior")).contains("\"bloqueado\": true", "\"intentosFallidos\": 5");
        assertThat((String) registro.get("valor_nuevo"))
                .contains("\"bloqueado\": false", "\"intentosFallidos\": 0", "\"origen\": \"script\"");
    }

    @Test
    void sinBloqueoNoCambiaNadaNiAudita() {
        LocalCreado creado = servicio.darDeAlta(datos("La Prueba", null, "Dueño La Prueba", EMAIL, CONTRASENA));
        jdbc.update("UPDATE usuario SET intentos_fallidos = 3 WHERE id = ?", creado.gerenteId());
        Long antes = maximo("registro_auditoria");

        assertThat(servicio.desbloquearGerente(EMAIL)).isEqualTo(ResultadoDelDesbloqueo.NO_ESTABA_BLOQUEADO);

        assertThat(jdbc.queryForObject("SELECT intentos_fallidos FROM usuario WHERE id = ?", Integer.class,
                creado.gerenteId())).isEqualTo(3);
        assertThat(maximo("registro_auditoria")).isEqualTo(antes);
    }

    @Test
    void unEmailQueNoEsDeUnGerenteNoCambiaNada() {
        jdbc.update("UPDATE usuario SET intentos_fallidos = 5, bloqueado_hasta = '2038-01-01 00:00:00' "
                + "WHERE email = 'admin@laesquina.test'");

        assertThat(servicio.desbloquearGerente("admin@laesquina.test"))
                .isEqualTo(ResultadoDelDesbloqueo.NO_ES_GERENTE);
        assertThat(servicio.desbloquearGerente("nadie@laprueba.test"))
                .isEqualTo(ResultadoDelDesbloqueo.NO_ES_GERENTE);

        assertThat(jdbc.queryForObject("SELECT intentos_fallidos FROM usuario WHERE email = 'admin@laesquina.test'",
                Integer.class)).isEqualTo(5);
        assertThat(auditoriaNueva()).isEmpty();
        assertThatThrownBy(() -> servicio.desbloquearGerente(" "))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessage("Falta el email");
    }

    private static DatosDeAltaDeLocal datos(String local, String direccion, String gerente, String email,
            String contrasena) {
        return new DatosDeAltaDeLocal(local, direccion, gerente, email, contrasena);
    }

    private List<Map<String, Object>> auditoriaNueva() {
        return jdbc.queryForList("SELECT * FROM registro_auditoria WHERE id > ?", ultimaAuditoria);
    }

    private Long maximo(String tabla) {
        return jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM " + tabla, Long.class);
    }
}
