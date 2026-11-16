package com.chitgo.usuarios.infraestructura.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.chitgo.usuarios.dominio.puertos.MozoDeTurno;

/**
 * El mozo de turno con el turno que cruza la medianoche (tarea 1 de la ST-23), contra el MySQL del Compose y la
 * seed. Se usa la mesa 4 de La Esquina, que es solo de Ana: cada test le deja a Ana un único turno y, si hace falta,
 * una ausencia. Las fechas son de noviembre de 2026: el domingo 15, el lunes 16 y el martes 17, en la hora del
 * local. Cada test corre en una transacción que se deshace al terminar, así la base queda como la seed.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class MozoDeTurnoAdapterTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDate DOMINGO = LocalDate.of(2026, 11, 15);
    private static final LocalDate LUNES = LocalDate.of(2026, 11, 16);
    private static final LocalDate MARTES = LocalDate.of(2026, 11, 17);

    @Autowired
    private MozoDeTurno mozoDeTurno;

    @Autowired
    private JdbcTemplate jdbc;

    private Long esquina;
    private Long ana;
    private Long mesa4;

    @BeforeEach
    void buscarIds() {
        esquina = jdbc.queryForObject("SELECT id FROM local WHERE nombre = 'La Esquina'", Long.class);
        ana = jdbc.queryForObject("SELECT e.id FROM empleado e JOIN usuario u ON u.id = e.usuario_id "
                + "WHERE u.nombre = 'Ana' AND e.local_id = ?", Long.class, esquina);
        mesa4 = jdbc.queryForObject("SELECT id FROM mesa WHERE local_id = ? AND numero = '4'", Long.class, esquina);
    }

    /** Le saca a Ana los turnos de la seed (todos los días de 00:00 a 23:59) y le deja solo este. */
    private void soloEsteTurno(String dia, String inicio, String fin) {
        jdbc.update("DELETE FROM turno WHERE empleado_id = ?", ana);
        jdbc.update("INSERT INTO turno (local_id, empleado_id, dia_semana, hora_inicio, hora_fin) VALUES (?, ?, ?, ?, ?)",
                esquina, ana, dia, inicio, fin);
    }

    private void ausencia(LocalDate desde, LocalDate hasta) {
        jdbc.update("""
                INSERT INTO ausencia (local_id, empleado_id, tipo, desde, hasta, creada_por_usuario_id)
                VALUES (?, ?, 'FRANCO', ?, ?, (SELECT id FROM usuario WHERE email = 'admin@laesquina.test'))""",
                esquina, ana, desde, hasta);
    }

    /** Si Ana atiende la mesa 4 ese día a esa hora del local. */
    private boolean deTurno(LocalDate fecha, String hora) {
        Instant ahora = LocalDateTime.of(fecha, LocalTime.parse(hora)).atZone(ZONA).toInstant();
        return mozoDeTurno.buscarParaMesa(mesa4, ahora).filter(ana::equals).isPresent();
    }

    // --- Los bordes de la tabla de la nota

    @Test
    void el_turno_del_lunes_de_12_a_16_va_de_las_12_a_las_15_59() {
        soloEsteTurno("LUN", "12:00:00", "16:00:00");

        assertThat(deTurno(LUNES, "11:59")).as("lunes 11:59").isFalse();
        assertThat(deTurno(LUNES, "12:00")).as("lunes 12:00").isTrue();
        assertThat(deTurno(LUNES, "15:59")).as("lunes 15:59").isTrue();
        assertThat(deTurno(LUNES, "16:00")).as("lunes 16:00").isFalse();
    }

    @Test
    void el_turno_del_lunes_de_20_a_02_sigue_hasta_las_01_59_del_martes() {
        soloEsteTurno("LUN", "20:00:00", "02:00:00");

        assertThat(deTurno(LUNES, "19:59")).as("lunes 19:59").isFalse();
        assertThat(deTurno(LUNES, "20:00")).as("lunes 20:00").isTrue();
        assertThat(deTurno(LUNES, "23:59")).as("lunes 23:59").isTrue();
        assertThat(deTurno(MARTES, "00:00")).as("martes 00:00").isTrue();
        assertThat(deTurno(MARTES, "01:59")).as("martes 01:59").isTrue();
        assertThat(deTurno(MARTES, "02:00")).as("martes 02:00").isFalse();
    }

    @Test
    void el_lunes_a_la_01_no_cuenta_el_turno_del_lunes_de_20_a_02_porque_ese_tramo_es_del_domingo() {
        soloEsteTurno("LUN", "20:00:00", "02:00:00");

        assertThat(deTurno(LUNES, "01:00")).isFalse();
    }

    @Test
    void el_turno_del_domingo_de_22_a_01_sigue_el_lunes_a_las_00_30() {
        soloEsteTurno("DOM", "22:00:00", "01:00:00");

        assertThat(deTurno(DOMINGO, "22:00")).as("domingo 22:00").isTrue();
        assertThat(deTurno(LUNES, "00:30")).as("lunes 00:30").isTrue();
        assertThat(deTurno(LUNES, "01:00")).as("lunes 01:00").isFalse();
    }

    @Test
    void el_turno_del_lunes_de_18_a_00_termina_a_la_medianoche() {
        soloEsteTurno("LUN", "18:00:00", "00:00:00");

        assertThat(deTurno(LUNES, "17:59")).as("lunes 17:59").isFalse();
        assertThat(deTurno(LUNES, "18:00")).as("lunes 18:00").isTrue();
        assertThat(deTurno(LUNES, "23:59")).as("lunes 23:59").isTrue();
        assertThat(deTurno(MARTES, "00:00")).as("martes 00:00").isFalse();
    }

    @Test
    void un_turno_del_martes_no_cuenta_el_lunes() {
        soloEsteTurno("MAR", "12:00:00", "16:00:00");

        assertThat(deTurno(LUNES, "13:00")).isFalse();
        assertThat(deTurno(MARTES, "13:00")).isTrue();
    }

    // --- La ausencia, la del día en que empezó el turno (decidido por Eze el 14/11)

    @Test
    void si_falta_el_lunes_su_turno_del_lunes_de_20_a_02_no_cuenta_tampoco_el_martes_a_la_01() {
        soloEsteTurno("LUN", "20:00:00", "02:00:00");
        ausencia(LUNES, LUNES);

        assertThat(deTurno(LUNES, "21:00")).as("lunes 21:00").isFalse();
        assertThat(deTurno(MARTES, "01:00")).as("martes 01:00").isFalse();
    }

    @Test
    void si_falta_solo_el_martes_el_martes_a_la_01_cuenta_porque_esta_terminando_el_turno_del_lunes() {
        soloEsteTurno("LUN", "20:00:00", "02:00:00");
        ausencia(MARTES, MARTES);

        assertThat(deTurno(LUNES, "21:00")).as("lunes 21:00").isTrue();
        assertThat(deTurno(MARTES, "01:00")).as("martes 01:00").isTrue();
    }

    @Test
    void una_ausencia_sin_fecha_de_vuelta_desde_el_lunes_tambien_tapa_el_tramo_del_martes() {
        soloEsteTurno("LUN", "20:00:00", "02:00:00");
        ausencia(LUNES, null);

        assertThat(deTurno(MARTES, "01:00")).isFalse();
    }

    @Test
    void con_una_ausencia_el_lunes_el_turno_del_lunes_de_12_a_16_no_cuenta() {
        soloEsteTurno("LUN", "12:00:00", "16:00:00");
        ausencia(LUNES, LUNES);

        assertThat(deTurno(LUNES, "13:00")).isFalse();
    }

    // --- La seed sigue igual

    @Test
    void con_los_turnos_de_la_seed_ana_atiende_la_mesa_4_el_lunes_a_las_13() {
        assertThat(deTurno(LUNES, "13:00")).isTrue();
        assertThat(deTurno(LUNES, "00:00")).isTrue();
    }
}
