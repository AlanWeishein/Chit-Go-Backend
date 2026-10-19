package com.chitgo.auditoria.infraestructura.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import com.chitgo.auditoria.dominio.RegistroAuditoria;
import com.chitgo.auditoria.dominio.puertos.Auditor;

/**
 * El Auditor contra el MySQL del Compose (tiene que estar levantado) y los datos de R__datos_de_prueba.sql.
 *
 * <p>Sin {@code @Transactional} a propósito: lo que hay que probar es qué queda guardado cuando la transacción
 * se confirma o se deshace, y eso solo se ve con el commit real. Cada test borra los registros que dejó y vuelve
 * la mesa como estaba.
 */
@SpringBootTest
@ActiveProfiles("dev")
class AuditorAdapterTest {

    private static final Long USUARIO_QUE_NO_EXISTE = 999_999L;

    /** Como lo haría un Service de HU-13: cambia la mesa y lo audita en la misma transacción. */
    static class InhabilitarMesa {

        private final JdbcTemplate jdbc;
        private final Auditor auditor;

        InhabilitarMesa(JdbcTemplate jdbc, Auditor auditor) {
            this.jdbc = jdbc;
            this.auditor = auditor;
        }

        @Transactional
        void inhabilitar(Long localId, Long usuarioId, Long mesaId) {
            // El UPDATE va a la base en el momento, dentro de la transacción: si después no se guarda, es porque
            // se deshizo.
            jdbc.update("UPDATE mesa SET inhabilitada = TRUE WHERE id = ?", mesaId);
            auditor.registrar(localId, usuarioId, "INHABILITAR_MESA", "MESA", mesaId,
                    Map.of("inhabilitada", false), Map.of("inhabilitada", true));
        }
    }

    @TestConfiguration
    static class ConServicioDePrueba {

        @Bean
        InhabilitarMesa inhabilitarMesa(JdbcTemplate jdbc, Auditor auditor) {
            return new InhabilitarMesa(jdbc, auditor);
        }
    }

    /** Lo que guardaría un Service: un DTO, no un String con el JSON armado a mano. */
    record EstadoDeMesa(String numero, boolean inhabilitada) {
    }

    @Autowired
    private Auditor auditor;

    @Autowired
    private RegistroAuditoriaJpaRepository registros;

    @Autowired
    private InhabilitarMesa inhabilitarMesa;

    @Autowired
    private TransactionTemplate transaccion;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    private long ultimoIdAntes;
    private Long laEsquina;
    private Long admin;
    private Long mesa5;

    @BeforeEach
    void anotarElUltimoRegistro() {
        ultimoIdAntes = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM registro_auditoria", Long.class);
        laEsquina = jdbc.queryForObject("SELECT id FROM local WHERE nombre = 'La Esquina'", Long.class);
        admin = jdbc.queryForObject("SELECT id FROM usuario WHERE email = 'admin@laesquina.test'", Long.class);
        mesa5 = jdbc.queryForObject("SELECT id FROM mesa WHERE local_id = ? AND numero = '5'", Long.class,
                laEsquina);
    }

    @AfterEach
    void dejarLaBaseComoEstaba() {
        jdbc.update("DELETE FROM registro_auditoria WHERE id > ?", ultimoIdAntes);
        jdbc.update("UPDATE mesa SET inhabilitada = FALSE WHERE id = ?", mesa5);
    }

    @Test
    void dentroDeUnaTransaccionGuardaElRegistroConLosValoresComoObjetosJson() {
        Instant antes = Instant.now();

        transaccion.executeWithoutResult(estado -> auditor.registrar(laEsquina, admin, "INHABILITAR_MESA", "MESA",
                mesa5, new EstadoDeMesa("5", false), new EstadoDeMesa("5", true)));

        // Leído con SQL, sin pasar por Hibernate: así se ve lo que de verdad quedó en la columna.
        Map<String, Object> fila = jdbc.queryForMap("""
                SELECT local_id, usuario_id, accion, entidad, entidad_id,
                       JSON_TYPE(valor_anterior) AS tipo_anterior, JSON_TYPE(valor_nuevo) AS tipo_nuevo,
                       valor_anterior ->> '$.inhabilitada' AS antes, valor_nuevo ->> '$.inhabilitada' AS despues,
                       valor_nuevo ->> '$.numero' AS numero, UNIX_TIMESTAMP(creado_en) AS creado_en
                FROM registro_auditoria WHERE id > ?""", ultimoIdAntes);

        assertThat(((Number) fila.get("local_id")).longValue()).isEqualTo(laEsquina);
        assertThat(((Number) fila.get("usuario_id")).longValue()).isEqualTo(admin);
        assertThat(fila.get("accion")).isEqualTo("INHABILITAR_MESA");
        assertThat(fila.get("entidad")).isEqualTo("MESA");
        assertThat(((Number) fila.get("entidad_id")).longValue()).isEqualTo(mesa5);
        // OBJECT y no STRING: el JSON se guardó como objeto, no como un texto entre comillas.
        assertThat(fila.get("tipo_anterior")).isEqualTo("OBJECT");
        assertThat(fila.get("tipo_nuevo")).isEqualTo("OBJECT");
        assertThat(fila.get("antes")).isEqualTo("false");
        assertThat(fila.get("despues")).isEqualTo("true");
        assertThat(fila.get("numero")).isEqualTo("5");
        assertThat(((Number) fila.get("creado_en")).longValue())
                .isBetween(antes.getEpochSecond() - 1, Instant.now().getEpochSecond() + 1);

        // Y leído con JPA vuelve el mismo JSON.
        Long id = jdbc.queryForObject("SELECT id FROM registro_auditoria WHERE id > ?", Long.class, ultimoIdAntes);
        RegistroAuditoria leido = registros.findById(id).orElseThrow();
        assertThat(json.readTree(leido.getValorNuevo()))
                .isEqualTo(json.readTree("{\"numero\": \"5\", \"inhabilitada\": true}"));
        assertThat(leido.getCreadoEn()).isNotNull();
    }

    @Test
    void sinValorAnteriorLaColumnaQuedaEnNull() {
        // Un alta: no había nada antes.
        transaccion.executeWithoutResult(estado -> auditor.registrar(laEsquina, admin, "CREAR_MESA", "MESA", mesa5,
                null, new EstadoDeMesa("5", false)));

        Map<String, Object> fila = jdbc.queryForMap("""
                SELECT valor_anterior IS NULL AS sin_anterior, JSON_TYPE(valor_nuevo) AS tipo_nuevo
                FROM registro_auditoria WHERE id > ?""", ultimoIdAntes);

        // NULL de SQL, no el JSON null (que daría JSON_TYPE = 'NULL' y la columna no vacía).
        assertThat(((Number) fila.get("sin_anterior")).intValue()).isEqualTo(1);
        assertThat(fila.get("tipo_nuevo")).isEqualTo("OBJECT");
    }

    @Test
    void sinTransaccionFallaYNoGuardaNada() {
        assertThatThrownBy(() -> auditor.registrar(laEsquina, admin, "INHABILITAR_MESA", "MESA", mesa5,
                new EstadoDeMesa("5", false), new EstadoDeMesa("5", true)))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(registrosNuevos()).isZero();
    }

    @Test
    void siLaTransaccionDeQuienLlamaSeDeshaceElRegistroTambien() {
        transaccion.executeWithoutResult(estado -> {
            auditor.registrar(laEsquina, admin, "INHABILITAR_MESA", "MESA", mesa5, new EstadoDeMesa("5", false),
                    new EstadoDeMesa("5", true));
            // Adentro de la transacción la fila está (el JdbcTemplate usa la misma conexión)...
            assertThat(registrosNuevos()).isEqualTo(1);
            estado.setRollbackOnly();
        });

        // ...y al deshacerla se va con el resto.
        assertThat(registrosNuevos()).isZero();
    }

    @Test
    void conUnUsuarioQueExisteSeGuardanLaAccionYElRegistro() {
        inhabilitarMesa.inhabilitar(laEsquina, admin, mesa5);

        assertThat(inhabilitada(mesa5)).isTrue();
        assertThat(registrosNuevos()).isEqualTo(1);
    }

    @Test
    void siFallaLaEscrituraDelRegistroLaAccionTampocoSeGuarda() {
        // CP-33-03: el usuario no existe, así que el INSERT falla por la FK después de que la mesa ya cambió.
        assertThatThrownBy(() -> inhabilitarMesa.inhabilitar(laEsquina, USUARIO_QUE_NO_EXISTE, mesa5))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(inhabilitada(mesa5)).isFalse();
        assertThat(registrosNuevos()).isZero();
    }

    private int registrosNuevos() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM registro_auditoria WHERE id > ?", Integer.class,
                ultimoIdAntes);
    }

    private boolean inhabilitada(Long mesaId) {
        return jdbc.queryForObject("SELECT inhabilitada FROM mesa WHERE id = ?", Boolean.class, mesaId);
    }
}
