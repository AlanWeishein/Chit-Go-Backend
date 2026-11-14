package com.chitgo.usuarios.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.chitgo.compartido.dominio.SolicitudInvalidaException;

/** HU-14, tarea 2 de la ST-22: un turno válido y cuándo dos turnos de la plantilla semanal se pisan. */
class TurnoTest {

    private static final Empleado PEDRO = new Empleado(1L,
            Usuario.nuevoEmpleado(1L, new Rol("MOZO", "Mozo"), "Pedro", "pedro", "hash", "hash"));

    private static Turno turno(DiaSemana dia, int inicio, int fin) {
        return new Turno(1L, PEDRO, dia, LocalTime.of(inicio, 0), LocalTime.of(fin, 0));
    }

    private static void sinChoques(Turno... turnos) {
        assertThatCode(() -> Turno.verificarQueNoSePisan(Arrays.asList(turnos))).doesNotThrowAnyException();
    }

    private static void chocan(String mensaje, Turno... turnos) {
        assertThatThrownBy(() -> Turno.verificarQueNoSePisan(Arrays.asList(turnos)))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessage(mensaje);
    }

    // --- Un turno

    @Test
    void unTurnoNoPuedeEmpezarYTerminarALaMismaHora() {
        assertThatThrownBy(() -> turno(DiaSemana.LUN, 12, 12))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessage("El turno no puede empezar y terminar a la misma hora");
    }

    @Test
    void unTurnoSinDiaOSinHoraNoSeArma() {
        assertThatThrownBy(() -> new Turno(1L, PEDRO, null, LocalTime.NOON, LocalTime.MIDNIGHT))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessage("Falta el día del turno");
        assertThatThrownBy(() -> new Turno(1L, PEDRO, DiaSemana.LUN, null, LocalTime.NOON))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessage("Falta la hora de inicio o de fin del turno");
        assertThatThrownBy(() -> new Turno(1L, PEDRO, DiaSemana.LUN, LocalTime.NOON, null))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessage("Falta la hora de inicio o de fin del turno");
    }

    @Test
    void CP_27_06_elTurnoDe20a02SeGuardaTalCualYTerminaAlDiaSiguiente() {
        Turno noche = turno(DiaSemana.LUN, 20, 2);

        assertThat(noche.getHoraInicio()).isEqualTo(LocalTime.of(20, 0));
        assertThat(noche.getHoraFin()).isEqualTo(LocalTime.of(2, 0));
        assertThat(noche.terminaAlDiaSiguiente()).isTrue();
        assertThat(turno(DiaSemana.LUN, 12, 16).terminaAlDiaSiguiente()).isFalse();
    }

    @Test
    void describirDaElDiaYLasHoras() {
        assertThat(turno(DiaSemana.LUN, 20, 2).describir()).isEqualTo("LUN 20:00-02:00");
    }

    // --- Turnos que se pisan

    @Test
    void CP_27_01_deLunesAViernesDe12a16NoSePisan() {
        sinChoques(turno(DiaSemana.LUN, 12, 16), turno(DiaSemana.MAR, 12, 16), turno(DiaSemana.MIE, 12, 16),
                turno(DiaSemana.JUE, 12, 16), turno(DiaSemana.VIE, 12, 16));
    }

    @Test
    void losTurnosDeLaSeedDe0000a2359TodosLosDiasNoSePisan() {
        List<Turno> semana = Arrays.stream(DiaSemana.values())
                .map(d -> new Turno(1L, PEDRO, d, LocalTime.MIDNIGHT, LocalTime.of(23, 59))).toList();

        assertThatCode(() -> Turno.verificarQueNoSePisan(semana)).doesNotThrowAnyException();
    }

    @Test
    void sinTurnosOConUnoSoloNoHayChoque() {
        sinChoques();
        sinChoques(turno(DiaSemana.LUN, 20, 2));
    }

    @Test
    void dosTurnosDelMismoDiaQueSeSuperponenSePisan() {
        chocan("Los turnos del lunes se pisan", turno(DiaSemana.LUN, 12, 16), turno(DiaSemana.LUN, 15, 20));
    }

    @Test
    void unTurnoDentroDeOtroSePisa() {
        chocan("Los turnos del miércoles se pisan", turno(DiaSemana.MIE, 10, 22), turno(DiaSemana.MIE, 12, 14));
    }

    @Test
    void elMismoTurnoDosVecesSePisa() {
        chocan("Los turnos del sábado se pisan", turno(DiaSemana.SAB, 12, 16), turno(DiaSemana.SAB, 12, 16));
    }

    @Test
    void dosTurnosQueSeTocanNoSePisan() {
        sinChoques(turno(DiaSemana.LUN, 12, 16), turno(DiaSemana.LUN, 16, 20));
    }

    @Test
    void unTurnoDelMismoDiaYUnoQueCruzaLaMedianocheSePisan() {
        chocan("Los turnos del viernes se pisan", turno(DiaSemana.VIE, 20, 2), turno(DiaSemana.VIE, 22, 23));
    }

    @Test
    void elQueCruzaLaMedianocheSePisaConElDelDiaSiguienteSiEmpiezaAntesDeQueTermine() {
        chocan("El turno del lunes se pisa con el del martes", turno(DiaSemana.MAR, 1, 5),
                turno(DiaSemana.LUN, 20, 2));
    }

    @Test
    void elQueCruzaLaMedianocheNoSePisaConElQueEmpiezaCuandoTermina() {
        sinChoques(turno(DiaSemana.LUN, 20, 2), turno(DiaSemana.MAR, 2, 6));
        sinChoques(turno(DiaSemana.LUN, 23, 0), turno(DiaSemana.MAR, 0, 8));
    }

    @Test
    void elDelDomingoQueCruzaLaMedianocheSigueEnElLunes() {
        chocan("El turno del lunes se pisa con el del domingo", turno(DiaSemana.DOM, 22, 3),
                turno(DiaSemana.LUN, 1, 4));
        sinChoques(turno(DiaSemana.DOM, 22, 3), turno(DiaSemana.LUN, 3, 8));
    }

    @Test
    void sePisaConEsSimetrico() {
        Turno noche = turno(DiaSemana.DOM, 22, 3);
        Turno madrugada = turno(DiaSemana.LUN, 1, 4);

        assertThat(noche.sePisaCon(madrugada)).isTrue();
        assertThat(madrugada.sePisaCon(noche)).isTrue();
        assertThat(turno(DiaSemana.LUN, 12, 16).sePisaCon(turno(DiaSemana.MAR, 12, 16))).isFalse();
    }
}
