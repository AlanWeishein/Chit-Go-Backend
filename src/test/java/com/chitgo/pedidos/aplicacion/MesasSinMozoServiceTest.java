package com.chitgo.pedidos.aplicacion;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.RelojDePrueba;
import com.chitgo.pedidos.dominio.puertos.MesaRepository;
import com.chitgo.pedidos.dominio.puertos.PublicadorDeEventos;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;
import com.chitgo.usuarios.dominio.puertos.MozoDeTurno;
import com.chitgo.usuarios.dominio.puertos.TurnosQueEmpiezan;

/** Lo que no se ve de punta a punta en MesaSinMozoTest: cuándo la pasada no consulta nada. */
class MesasSinMozoServiceTest {

    private final TurnosQueEmpiezan turnosQueEmpiezan = mock(TurnosQueEmpiezan.class);
    private final MesaRepository mesas = mock(MesaRepository.class);
    private final MozoDeTurno mozoDeTurno = mock(MozoDeTurno.class);
    private final PublicadorDeEventos eventos = mock(PublicadorDeEventos.class);
    private final MesasSinMozoService servicio = new MesasSinMozoService(turnosQueEmpiezan, mesas, mozoDeTurno,
            eventos, new RelojDePrueba(Instant.parse("2026-11-16T15:01:00Z")));

    @Test
    void en_un_minuto_sin_turnos_que_empiecen_no_se_consulta_ninguna_mesa() {
        when(turnosQueEmpiezan.localesConTurnoQueEmpieza(any())).thenReturn(Set.of());

        servicio.avisarAlEmpezarLosTurnos();

        verifyNoInteractions(mesas, mozoDeTurno, eventos);
    }

    @Test
    void el_gerente_sin_local_no_puede_pedir_la_lista() {
        UsuarioAutenticado gerente = new UsuarioAutenticado(1L, "GERENTE", null, false, Instant.now());

        assertThatThrownBy(() -> servicio.listar(gerente)).isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(mesas);
    }
}
