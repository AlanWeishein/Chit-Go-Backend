package com.chitgo.usuarios.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.chitgo.compartido.dominio.SolicitudInvalidaException;

/** HU-14, tarea 2 de la ST-22: las fechas de una ausencia y cómo se termina, sin borrarla. */
class AusenciaTest {

    private static final Empleado ANA = new Empleado(1L,
            Usuario.nuevoEmpleado(1L, new Rol("MOZO", "Mozo"), "Ana", "ana", "hash", "hash"));
    private static final LocalDate DESDE = LocalDate.of(2026, 11, 14);

    private static Ausencia vacaciones(LocalDate hasta) {
        return new Ausencia(1L, ANA, TipoAusencia.VACACIONES, DESDE, hasta, 9L);
    }

    @Test
    void unaAusenciaPuedeNoTenerFechaDeVuelta() {
        Ausencia ausencia = vacaciones(null);

        assertThat(ausencia.getHasta()).isNull();
        assertThat(ausencia.getCreadaPorUsuarioId()).isEqualTo(9L);
    }

    @Test
    void unaAusenciaDeUnSoloDiaEmpiezaYTerminaElMismoDia() {
        assertThat(vacaciones(DESDE).getHasta()).isEqualTo(DESDE);
    }

    @Test
    void unaAusenciaNoPuedeTerminarAntesDeEmpezar() {
        assertThatThrownBy(() -> vacaciones(DESDE.minusDays(1)))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessage("La ausencia no puede terminar antes de empezar");
    }

    @Test
    void sinTipoOSinInicioNoSeArma() {
        assertThatThrownBy(() -> new Ausencia(1L, ANA, null, DESDE, null, 9L))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessage("Falta el tipo de ausencia");
        assertThatThrownBy(() -> new Ausencia(1L, ANA, TipoAusencia.FRANCO, null, null, 9L))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessage("Falta la fecha de inicio");
    }

    @Test
    void terminarUnaSinFechaDeVueltaLePoneElHasta() {
        Ausencia ausencia = vacaciones(null);

        assertThat(ausencia.cambiarHasta(DESDE.plusDays(3))).isTrue();
        assertThat(ausencia.getHasta()).isEqualTo(DESDE.plusDays(3));
        assertThat(ausencia.getDesde()).isEqualTo(DESDE);
    }

    @Test
    void cambiarLaFechaDeVueltaPorLaMismaDiceQueNoCambioNada() {
        Ausencia ausencia = vacaciones(DESDE.plusDays(5));

        assertThat(ausencia.cambiarHasta(DESDE.plusDays(5))).isFalse();
        assertThat(ausencia.cambiarHasta(DESDE)).isTrue();
        assertThat(ausencia.getHasta()).isEqualTo(DESDE);
    }

    @Test
    void elNuevoHastaNoPuedeFaltarNiSerAnteriorAlInicioYNoCambiaNada() {
        Ausencia ausencia = vacaciones(DESDE.plusDays(5));

        assertThatThrownBy(() -> ausencia.cambiarHasta(null))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessage("Falta la fecha de fin");
        assertThatThrownBy(() -> ausencia.cambiarHasta(DESDE.minusDays(1)))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessage("La ausencia no puede terminar antes de empezar");
        assertThat(ausencia.getHasta()).isEqualTo(DESDE.plusDays(5));
    }
}
