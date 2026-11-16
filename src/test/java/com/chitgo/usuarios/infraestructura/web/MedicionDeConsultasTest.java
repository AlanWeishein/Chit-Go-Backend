package com.chitgo.usuarios.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.chitgo.usuarios.ContadorDeSql;

/**
 * Cuántas consultas hace HU-14 (tarea 4 de la ST-22, el optimizador): el listado de empleados, la lista del login con
 * el filtro de la ausencia y /auth/pin con la ausencia. Cada medición imprime sus SQL numerados (líneas MEDICION).
 * Los empleados de la medición se crean por JdbcTemplate con el prefijo "zz-opt-" y se borran al terminar; al final
 * se controla que la base quedó como la seed.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
        + "com.chitgo.usuarios.ContadorDeSql")
class MedicionDeConsultasTest extends EmpleadosHttpBase {

    private static final String PREFIJO = "zz-opt-";

    @AfterEach
    void controlarQueLaBaseEsLaSeed() {
        dejarLaBaseComoLaSeed();
        assertThat(contar("usuario")).isEqualTo(8);
        assertThat(contar("empleado")).isEqualTo(6);
        assertThat(contar("turno")).isEqualTo(21);
        assertThat(contar("mozo_mesa")).isEqualTo(7);
        assertThat(contar("ausencia")).isZero();
        assertThat(contar("cuenta")).isZero();
        assertThat(contar("registro_auditoria")).isZero();
    }

    @Test
    void elListadoDelAdminNoHaceUnaConsultaPorEmpleado() throws Exception {
        crearEmpleados(16, 0);
        Cookie admin = sesionDelAdmin();

        List<String> con20 = medir("listado 20 empleados, page=0 size=20",
                get(EMPLEADOS + "?page=0&size=20").cookie(admin));
        crearEmpleados(20, 16);
        List<String> con40 = medir("listado 40 empleados, page=0 size=20",
                get(EMPLEADOS + "?page=0&size=20").cookie(admin));
        List<String> pagina1 = medir("listado 40 empleados, page=1 size=20",
                get(EMPLEADOS + "?page=1&size=20").cookie(admin));
        medir("listado 40 empleados, page=0 size=19", get(EMPLEADOS + "?page=0&size=19").cookie(admin));

        // Guardia contra un N+1; si se suma una consulta a propósito, ajustar el tope y anotar por qué.
        assertThat(delServicio(con20)).hasSizeLessThanOrEqualTo(4);
        assertThat(delServicio(con40)).hasSameSizeAs(delServicio(con20));
        assertThat(delServicio(pagina1)).hasSameSizeAs(delServicio(con20));
        // Lo mismo para el total (filtro de sesión más servicio).
        assertThat(con20).hasSizeLessThanOrEqualTo(7);
        assertThat(con40).hasSizeLessThanOrEqualTo(7);
    }

    @Test
    void laListaDelLoginTieneUnaSolaConsultaDeLista() throws Exception {
        crearEmpleados(16, 0);

        List<String> conAusencias = medir("lista del login, con ausencias", get(STAFF + TOKEN_LA_ESQUINA));
        explicarLaListaDelLogin();
        jdbc.update("DELETE FROM ausencia");
        List<String> sinAusencias = medir("lista del login, sin ausencias", get(STAFF + TOKEN_LA_ESQUINA));

        // Guardia contra un N+1; si se suma una consulta a propósito, ajustar el tope y anotar por qué.
        // Son el local por su token y una sola consulta de lista.
        assertThat(conAusencias).hasSizeLessThanOrEqualTo(2);
        assertThat(sinAusencias).hasSizeLessThanOrEqualTo(2);
        assertThat(sobreAusencia(conAusencias)).isEqualTo(1);
        assertThat(sobreAusencia(sinAusencias)).isEqualTo(1);
    }

    @Test
    void elPinConUnaAusenciaVigenteSumaUnaSolaConsultaSobreAusencia() throws Exception {
        Long juan = usuarioDe("Juan");
        Cookie dispositivo = dispositivoDe(juan);

        List<String> bien = medirPin("pin correcto, sin ausencia", juan, "1234", dispositivo, 200);
        insertarAusencia(empleadoDe("Juan"), hoy().minusDays(1), hoy().plusDays(5));
        List<String> conAusencia = medirPin("pin correcto, con ausencia vigente", juan, "1234", dispositivo, 404);
        jdbc.update("DELETE FROM ausencia");
        List<String> malo = medirPin("pin malo, sin ausencia", juan, "9999", dispositivo, 401);

        System.out.println("MEDICION consultas sobre ausencia: bien=" + sobreAusencia(bien) + " conAusencia="
                + sobreAusencia(conAusencia) + " malo=" + sobreAusencia(malo));
        // Guardia contra un N+1; si se suma una consulta a propósito, ajustar el tope y anotar por qué.
        assertThat(bien).hasSizeLessThanOrEqualTo(4);
        assertThat(conAusencia).hasSizeLessThanOrEqualTo(4);
        assertThat(malo).hasSizeLessThanOrEqualTo(5); // suma el update de los intentos
        assertThat(sobreAusencia(bien)).isEqualTo(1);
        assertThat(sobreAusencia(conAusencia)).isEqualTo(1);
        assertThat(sobreAusencia(malo)).isEqualTo(1);
    }

    // --- Ayudas

    private List<String> medir(String etiqueta, MockHttpServletRequestBuilder pedido) throws Exception {
        ContadorDeSql.reiniciar();
        long desde = System.nanoTime();
        mvc.perform(pedido).andExpect(status().isOk());
        return registrar(etiqueta, desde);
    }

    private List<String> medirPin(String etiqueta, Long usuarioId, String pin, Cookie dispositivo, int estado)
            throws Exception {
        Cookie xsrf = xsrf();
        String cuerpo = """
                {"tokenStaff": "%s", "usuarioId": %d, "pin": "%s"}""".formatted(TOKEN_LA_ESQUINA, usuarioId, pin);
        MockHttpServletRequestBuilder pedido = post("/api/v1/auth/pin").cookie(xsrf, dispositivo)
                .header("X-XSRF-TOKEN", xsrf.getValue()).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        ContadorDeSql.reiniciar();
        long desde = System.nanoTime();
        mvc.perform(pedido).andExpect(status().is(estado));
        return registrar(etiqueta, desde);
    }

    private List<String> registrar(String etiqueta, long desde) {
        long ms = (System.nanoTime() - desde) / 1_000_000;
        List<String> consultas = ContadorDeSql.consultas();
        System.out.println("MEDICION == " + etiqueta + ": " + consultas.size() + " consultas, "
                + delServicio(consultas).size() + " de empleado/turno/ausencia, " + ms + " ms");
        for (int i = 0; i < consultas.size(); i++) {
            System.out.println("MEDICION " + (i + 1) + ". " + consultas.get(i).replaceAll("\\s+", " "));
        }
        return consultas;
    }

    private List<String> delServicio(List<String> consultas) {
        return consultas.stream().filter(s -> s.contains("empleado") || s.contains("turno") || s.contains("ausencia"))
                .toList();
    }

    private long sobreAusencia(List<String> consultas) {
        return consultas.stream().filter(s -> s.contains("ausencia")).count();
    }

    private void explicarLaListaDelLogin() {
        List<Map<String, Object>> plan = jdbc.queryForList("""
                EXPLAIN SELECT u.id FROM usuario u JOIN empleado e ON e.usuario_id = u.id
                WHERE u.local_id = ? AND u.activo = TRUE AND NOT EXISTS (SELECT 1 FROM ausencia a
                WHERE a.empleado_id = e.id AND a.desde <= CURDATE() AND (a.hasta IS NULL OR a.hasta >= CURDATE()))""",
                localDe("La Esquina"));
        plan.forEach(fila -> System.out.println("MEDICION EXPLAIN " + fila));
    }

    /** {@code cantidad} gestionables más, con 3 a 5 turnos; en la primera tanda, cinco traen una ausencia. */
    private void crearEmpleados(int cantidad, int primero) {
        Long local = localDe("La Esquina");
        Long admin = jdbc.queryForObject("SELECT id FROM usuario WHERE email = 'admin@laesquina.test'", Long.class);
        String hash = jdbc.queryForObject("SELECT hash_password FROM usuario WHERE id = ?", String.class, admin);
        String[] roles = {"MOZO", "CAJA", "COCINA"};
        String[] dias = {"LUN", "MAR", "MIE", "JUE", "VIE"};
        LocalDate hoy = hoy();
        for (int i = primero; i < primero + cantidad; i++) {
            String rol = roles[i % 3];
            String nombre = PREFIJO + String.format("%02d", i);
            jdbc.update("""
                    INSERT INTO usuario (local_id, rol_id, nombre, nombre_usuario, email, hash_password, pin_hash,
                    debe_cambiar_credencial) VALUES (?, (SELECT id FROM rol WHERE codigo = ?), ?, ?, NULL, ?, ?, FALSE)""",
                    local, rol, nombre, "MOZO".equals(rol) ? "zzopt" + i : null, hash, hash);
            Long usuario = jdbc.queryForObject("SELECT id FROM usuario WHERE nombre = ? AND local_id = ?", Long.class,
                    nombre, local);
            jdbc.update("INSERT INTO empleado (local_id, usuario_id) VALUES (?, ?)", local, usuario);
            Long empleado = jdbc.queryForObject("SELECT id FROM empleado WHERE usuario_id = ?", Long.class, usuario);
            for (int t = 0; t < 3 + i % 3; t++) {
                jdbc.update("""
                        INSERT INTO turno (local_id, empleado_id, dia_semana, hora_inicio, hora_fin)
                        VALUES (?, ?, ?, '12:00:00', '16:00:00')""", local, empleado, dias[t]);
            }
            if (primero == 0 && i < 5) {
                switch (i) {
                    case 0 -> insertarAusencia(empleado, hoy.minusDays(1), null);
                    case 1, 2 -> insertarAusencia(empleado, hoy.minusDays(1), hoy.plusDays(5));
                    case 3 -> insertarAusencia(empleado, hoy.minusDays(10), hoy.minusDays(2));
                    default -> insertarAusencia(empleado, hoy.plusDays(1), hoy.plusDays(4));
                }
            }
        }
    }

    private void insertarAusencia(Long empleado, LocalDate desde, LocalDate hasta) {
        jdbc.update("""
                INSERT INTO ausencia (local_id, empleado_id, tipo, desde, hasta, creada_por_usuario_id)
                VALUES (?, ?, 'VACACIONES', ?, ?, (SELECT id FROM usuario WHERE email = 'admin@laesquina.test'))""",
                localDe("La Esquina"), empleado, desde, hasta);
    }

    private int contar(String tabla) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Integer.class);
    }
}
