package com.chitgo.usuarios.aplicacion;

import static com.chitgo.pruebas.Entidades.conId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.chitgo.auditoria.dominio.puertos.Auditor;
import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.compartido.dominio.ReglaDeNegocioException;
import com.chitgo.compartido.dominio.RelojDePrueba;
import com.chitgo.compartido.dominio.SolicitudInvalidaException;
import com.chitgo.usuarios.aplicacion.dto.AusenciaDto;
import com.chitgo.usuarios.aplicacion.dto.DatosDeAusencia;
import com.chitgo.usuarios.aplicacion.dto.DatosDeTurno;
import com.chitgo.usuarios.aplicacion.dto.EmpleadoDetalleDto;
import com.chitgo.usuarios.dominio.Ausencia;
import com.chitgo.usuarios.dominio.DiaSemana;
import com.chitgo.usuarios.dominio.Empleado;
import com.chitgo.usuarios.dominio.MozoMesa;
import com.chitgo.usuarios.dominio.Rol;
import com.chitgo.usuarios.dominio.TipoAusencia;
import com.chitgo.usuarios.dominio.Turno;
import com.chitgo.usuarios.dominio.Usuario;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;
import com.chitgo.usuarios.dominio.puertos.CodificadorDeClaves;
import com.chitgo.usuarios.dominio.puertos.EmpleadoRepository;
import com.chitgo.usuarios.dominio.puertos.MesasDelLocal;
import com.chitgo.usuarios.dominio.puertos.RolRepository;
import com.chitgo.usuarios.dominio.puertos.UsuarioRepository;

/**
 * HU-14, tarea 2 de la ST-22: turnos, mesas del mozo y ausencias, con mocks. Juan (id 1, Mozo, mesas 1, 2 y 3) y
 * Carla (id 2, Caja) están activos en La Esquina (local 1); Hugo (id 3, Cocina) está dado de baja. Las mesas 1 a 6
 * son del local; la 90 es de El Puerto. Lo de otro local no existe para los puertos: devuelven vacío.
 */
class GestionarTurnosMesasYAusenciasTest {

    private static final Instant AHORA = Instant.parse("2026-11-14T15:00:00Z");
    private static final LocalDate HOY = LocalDate.of(2026, 11, 14);
    private static final Long LOCAL = 1L;
    private static final Long ADMIN_ID = 2L;
    private static final Long OTRO_LOCAL_ID = 99L;
    private static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(ADMIN_ID, "ADMIN", LOCAL, false,
            AHORA.plusSeconds(600));
    private static final Set<Long> MESAS_DEL_LOCAL = Set.of(1L, 2L, 3L, 4L, 5L, 6L);

    private final EmpleadoRepository empleados = mock(EmpleadoRepository.class);
    private final MesasDelLocal mesas = mock(MesasDelLocal.class);
    private final Auditor auditor = mock(Auditor.class);

    /** Los turnos "guardados" de cada empleado, para que turnosDe devuelva lo último que se reemplazó. */
    private final Map<Long, List<Turno>> turnosGuardados = new HashMap<>();

    private Empleado juan;
    private Empleado carla;
    private Empleado hugo;
    private GestionarEmpleadosService servicio;

    @BeforeEach
    void preparar() {
        servicio = new GestionarEmpleadosService(empleados, mock(UsuarioRepository.class), mock(RolRepository.class),
                mesas, mock(CodificadorDeClaves.class), auditor, new RelojDePrueba(AHORA));
        juan = empleado(1L, "MOZO", "Juan", "juan");
        carla = empleado(2L, "CAJA", "Carla", null);
        hugo = empleado(3L, "COCINA", "Hugo", null);
        hugo.darDeBaja();
        for (Long mesa : List.of(1L, 2L, 3L)) {
            new MozoMesa(LOCAL, juan, mesa);
        }
        for (Empleado e : List.of(juan, carla, hugo)) {
            when(empleados.buscarPorId(e.getId(), LOCAL)).thenReturn(Optional.of(e));
            when(empleados.buscarParaModificar(e.getId(), LOCAL)).thenReturn(Optional.of(e));
        }
        turnosGuardados.put(juan.getId(), List.of(turno(juan, DiaSemana.LUN, 12, 16)));
        when(empleados.turnosDe(anyCollection(), anyLong())).thenAnswer(inv -> {
            Collection<Long> ids = inv.getArgument(0);
            return ids.stream().flatMap(id -> turnosGuardados.getOrDefault(id, List.of()).stream()).toList();
        });
        doAnswer(inv -> {
            turnosGuardados.put(inv.getArgument(0), inv.getArgument(2));
            return null;
        }).when(empleados).reemplazarTurnos(anyLong(), anyLong(), any());
        when(empleados.ausenciasDe(anyLong(), anyLong())).thenReturn(List.of());
        when(empleados.guardarAusencia(any(Ausencia.class))).thenAnswer(inv -> {
            Ausencia a = inv.getArgument(0);
            return a.getId() == null ? conId(a, 70L) : a;
        });
        when(mesas.deEsteLocal(anyCollection(), eq(LOCAL))).thenAnswer(inv -> {
            Set<Long> pedidas = new HashSet<>(inv.<Collection<Long>>getArgument(0));
            pedidas.retainAll(MESAS_DEL_LOCAL);
            return pedidas;
        });
    }

    private static Empleado empleado(Long id, String codigoRol, String nombre, String nombreUsuario) {
        Usuario usuario = conId(Usuario.nuevoEmpleado(LOCAL, new Rol(codigoRol, codigoRol), nombre, nombreUsuario,
                nombreUsuario == null ? null : "hashPass", "hashPin"), id + 10);
        return conId(new Empleado(LOCAL, usuario), id);
    }

    private static Turno turno(Empleado e, DiaSemana dia, int inicio, int fin) {
        return new Turno(LOCAL, e, dia, LocalTime.of(inicio, 0), LocalTime.of(fin, 0));
    }

    private static DatosDeTurno datos(String dia, int inicio, int fin) {
        return new DatosDeTurno(dia, LocalTime.of(inicio, 0), LocalTime.of(fin, 0));
    }

    private Ausencia ausenciaDeJuan(Long id, LocalDate desde, LocalDate hasta) {
        Ausencia a = conId(new Ausencia(LOCAL, juan, TipoAusencia.VACACIONES, desde, hasta, ADMIN_ID), id);
        when(empleados.buscarAusencia(id, juan.getId(), LOCAL)).thenReturn(Optional.of(a));
        return a;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object>[] auditado(String accion, String entidad, Long entidadId) {
        ArgumentCaptor<Object> antes = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> despues = ArgumentCaptor.forClass(Object.class);
        verify(auditor).registrar(eq(LOCAL), eq(ADMIN_ID), eq(accion), eq(entidad), eq(entidadId), antes.capture(),
                despues.capture());
        return new Map[] {(Map<String, Object>) antes.getValue(), (Map<String, Object>) despues.getValue()};
    }

    private void sinCambiosDeTurnos() {
        verify(empleados, never()).reemplazarTurnos(anyLong(), anyLong(), any());
        verifyNoInteractions(auditor);
    }

    // --- Turnos

    @Test
    void CP_27_06_el_turno_de_20_a_02_se_guarda_tal_cual_y_se_audita_antes_y_despues() {
        EmpleadoDetalleDto dto = servicio.reemplazarTurnos(ADMIN, juan.getId(),
                List.of(datos("LUN", 20, 2), datos("MAR", 12, 16)));

        assertThat(dto.turnos()).extracting(t -> t.dia() + " " + t.horaInicio() + "-" + t.horaFin())
                .containsExactly("LUN 20:00-02:00", "MAR 12:00-16:00");
        Map<String, Object>[] valores = auditado("CAMBIAR_TURNOS", "EMPLEADO", juan.getId());
        assertThat(valores[0]).isEqualTo(Map.of("turnos", List.of("LUN 12:00-16:00")));
        assertThat(valores[1]).isEqualTo(Map.of("turnos", List.of("LUN 20:00-02:00", "MAR 12:00-16:00")));
    }

    @Test
    void los_turnos_nuevos_son_del_local_y_del_empleado() {
        servicio.reemplazarTurnos(ADMIN, juan.getId(), List.of(datos("vie", 9, 13)));

        assertThat(turnosGuardados.get(juan.getId())).singleElement().satisfies(t -> {
            assertThat(t.getLocalId()).isEqualTo(LOCAL);
            assertThat(t.getEmpleado()).isSameAs(juan);
            assertThat(t.getDiaSemana()).isEqualTo(DiaSemana.VIE);
        });
    }

    @Test
    void la_lista_vacia_lo_deja_sin_turnos() {
        EmpleadoDetalleDto dto = servicio.reemplazarTurnos(ADMIN, juan.getId(), List.of());

        verify(empleados).reemplazarTurnos(juan.getId(), LOCAL, List.of());
        assertThat(dto.turnos()).isEmpty();
        assertThat(auditado("CAMBIAR_TURNOS", "EMPLEADO", juan.getId())[1]).isEqualTo(Map.of("turnos", List.of()));
    }

    @Test
    void los_mismos_turnos_no_guardan_ni_auditan() {
        servicio.reemplazarTurnos(ADMIN, juan.getId(), List.of(datos("LUN", 12, 16)));

        sinCambiosDeTurnos();
    }

    @Test
    void caja_y_cocina_tambien_tienen_turnos() {
        EmpleadoDetalleDto dto = servicio.reemplazarTurnos(ADMIN, carla.getId(), List.of(datos("SAB", 18, 23)));

        assertThat(dto.turnos()).hasSize(1);
    }

    @Test
    void dos_turnos_que_se_pisan_dan_400_y_no_se_guarda_ninguno() {
        assertThatThrownBy(() -> servicio.reemplazarTurnos(ADMIN, juan.getId(),
                List.of(datos("LUN", 12, 16), datos("LUN", 15, 20))))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessage("Los turnos del lunes se pisan");
        assertThatThrownBy(() -> servicio.reemplazarTurnos(ADMIN, juan.getId(),
                List.of(datos("LUN", 20, 2), datos("MAR", 1, 5))))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessage("El turno del lunes se pisa con el del martes");
        sinCambiosDeTurnos();
    }

    @Test
    void un_turno_que_empieza_y_termina_a_la_misma_hora_da_400() {
        assertThatThrownBy(() -> servicio.reemplazarTurnos(ADMIN, juan.getId(), List.of(datos("LUN", 12, 12))))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessage("El turno no puede empezar y terminar a la misma hora");
        sinCambiosDeTurnos();
    }

    @Test
    void un_dia_inventado_o_que_falta_da_400_con_mensaje() {
        assertThatThrownBy(() -> servicio.reemplazarTurnos(ADMIN, juan.getId(), List.of(datos("LUNES", 12, 16))))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessage("El día tiene que ser LUN, MAR, MIE, JUE, VIE, SAB o DOM");
        assertThatThrownBy(() -> servicio.reemplazarTurnos(ADMIN, juan.getId(), List.of(datos(" ", 12, 16))))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessage("Falta el día del turno");
        assertThatThrownBy(() -> servicio.reemplazarTurnos(ADMIN, juan.getId(),
                List.of(new DatosDeTurno("LUN", null, LocalTime.NOON))))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessage("Falta la hora de inicio o de fin del turno");
        sinCambiosDeTurnos();
    }

    @Test
    void sin_lista_o_con_un_turno_nulo_da_400() {
        assertThatThrownBy(() -> servicio.reemplazarTurnos(ADMIN, juan.getId(), null))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessage("Faltan los turnos");
        assertThatThrownBy(() -> servicio.reemplazarTurnos(ADMIN, juan.getId(), Arrays.asList(datos("LUN", 1, 2),
                null)))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessage("Falta un turno");
        sinCambiosDeTurnos();
    }

    @Test
    void los_turnos_de_un_dado_de_baja_dan_409_y_los_de_otro_local_404() {
        assertThatThrownBy(() -> servicio.reemplazarTurnos(ADMIN, hugo.getId(), List.of(datos("LUN", 12, 16))))
                .isInstanceOf(ReglaDeNegocioException.class).hasMessage("El empleado está dado de baja");
        assertThatThrownBy(() -> servicio.reemplazarTurnos(ADMIN, OTRO_LOCAL_ID, List.of()))
                .isInstanceOf(NoEncontradoException.class).hasMessage("El empleado no existe");
        sinCambiosDeTurnos();
    }

    // --- Mesas

    @Test
    void CP_27_02_las_mesas_se_reemplazan_sin_repetir_y_se_audita_antes_y_despues() {
        EmpleadoDetalleDto dto = servicio.reemplazarMesas(ADMIN, juan.getId(), List.of(5L, 3L, 5L));

        assertThat(dto.mesaIds()).containsExactly(3L, 5L);
        assertThat(juan.getMesaIds()).containsExactlyInAnyOrder(3L, 5L);
        Map<String, Object>[] valores = auditado("CAMBIAR_MESAS", "EMPLEADO", juan.getId());
        assertThat(valores[0]).isEqualTo(Map.of("mesaIds", List.of(1L, 2L, 3L)));
        assertThat(valores[1]).isEqualTo(Map.of("mesaIds", List.of(3L, 5L)));
    }

    @Test
    void una_mesa_de_otro_local_o_que_no_existe_da_404_y_no_se_guarda_ninguna() {
        assertThatThrownBy(() -> servicio.reemplazarMesas(ADMIN, juan.getId(), List.of(5L, 90L)))
                .isInstanceOf(NoEncontradoException.class).hasMessage("La mesa no existe");
        assertThatThrownBy(() -> servicio.reemplazarMesas(ADMIN, juan.getId(), List.of(12345L)))
                .isInstanceOf(NoEncontradoException.class).hasMessage("La mesa no existe");

        assertThat(juan.getMesaIds()).containsExactlyInAnyOrder(1L, 2L, 3L);
        verifyNoInteractions(auditor);
    }

    @Test
    void la_lista_vacia_lo_deja_sin_mesas_sin_consultar_las_mesas() {
        EmpleadoDetalleDto dto = servicio.reemplazarMesas(ADMIN, juan.getId(), List.of());

        assertThat(dto.mesaIds()).isEmpty();
        verifyNoInteractions(mesas);
        assertThat(auditado("CAMBIAR_MESAS", "EMPLEADO", juan.getId())[1]).isEqualTo(Map.of("mesaIds", List.of()));
    }

    @Test
    void las_mismas_mesas_no_auditan() {
        servicio.reemplazarMesas(ADMIN, juan.getId(), List.of(3L, 1L, 2L));

        assertThat(juan.getMesaIds()).containsExactlyInAnyOrder(1L, 2L, 3L);
        verifyNoInteractions(auditor);
    }

    @Test
    void solo_el_mozo_tiene_mesas() {
        assertThatThrownBy(() -> servicio.reemplazarMesas(ADMIN, carla.getId(), List.of(5L)))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessage("Solo el mozo tiene mesas asignadas");
        verifyNoInteractions(mesas, auditor);
    }

    @Test
    void sin_lista_o_con_un_id_nulo_da_400() {
        assertThatThrownBy(() -> servicio.reemplazarMesas(ADMIN, juan.getId(), null))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessage("Faltan las mesas");
        List<Long> conNulo = new ArrayList<>(List.of(5L));
        conNulo.add(null);
        assertThatThrownBy(() -> servicio.reemplazarMesas(ADMIN, juan.getId(), conNulo))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessage("Falta el id de una mesa");
        assertThat(juan.getMesaIds()).containsExactlyInAnyOrder(1L, 2L, 3L);
        verifyNoInteractions(mesas, auditor);
    }

    @Test
    void las_mesas_de_un_dado_de_baja_dan_409_y_las_de_otro_local_404() {
        assertThatThrownBy(() -> servicio.reemplazarMesas(ADMIN, hugo.getId(), List.of(5L)))
                .isInstanceOf(ReglaDeNegocioException.class).hasMessage("El empleado está dado de baja");
        assertThatThrownBy(() -> servicio.reemplazarMesas(ADMIN, OTRO_LOCAL_ID, List.of(5L)))
                .isInstanceOf(NoEncontradoException.class).hasMessage("El empleado no existe");
        verifyNoInteractions(mesas, auditor);
    }

    // --- Ausencias

    @Test
    void CP_27_05_cargar_vacaciones_con_fecha_de_vuelta_la_firma_el_admin_y_se_audita() {
        AusenciaDto dto = servicio.cargarAusencia(ADMIN, juan.getId(),
                new DatosDeAusencia("VACACIONES", HOY, HOY.plusDays(7)));

        ArgumentCaptor<Ausencia> guardada = ArgumentCaptor.forClass(Ausencia.class);
        verify(empleados).guardarAusencia(guardada.capture());
        assertThat(guardada.getValue().getCreadaPorUsuarioId()).isEqualTo(ADMIN_ID);
        assertThat(guardada.getValue().getLocalId()).isEqualTo(LOCAL);
        assertThat(guardada.getValue().getEmpleado()).isSameAs(juan);
        assertThat(dto).isEqualTo(new AusenciaDto(70L, TipoAusencia.VACACIONES, HOY, HOY.plusDays(7)));

        Map<String, Object>[] valores = auditado("CREAR_AUSENCIA", "AUSENCIA", 70L);
        assertThat(valores[0]).isNull();
        assertThat(valores[1]).containsEntry("empleadoId", juan.getId()).containsEntry("tipo", "VACACIONES")
                .containsEntry("desde", "2026-11-14").containsEntry("hasta", "2026-11-21");
    }

    @Test
    void una_ausencia_sin_fecha_de_vuelta_y_con_el_tipo_en_minusculas_se_carga() {
        AusenciaDto dto = servicio.cargarAusencia(ADMIN, carla.getId(), new DatosDeAusencia("baja_temporal", HOY,
                null));

        assertThat(dto.tipo()).isEqualTo(TipoAusencia.BAJA_TEMPORAL);
        assertThat(dto.hasta()).isNull();
        assertThat(auditado("CREAR_AUSENCIA", "AUSENCIA", 70L)[1]).containsEntry("hasta", null);
    }

    @Test
    void los_datos_que_faltan_o_no_cierran_dan_400_y_no_guardan() {
        assertThatThrownBy(() -> servicio.cargarAusencia(ADMIN, juan.getId(), new DatosDeAusencia(null, HOY, null)))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessage("Falta el tipo de ausencia");
        assertThatThrownBy(() -> servicio.cargarAusencia(ADMIN, juan.getId(),
                new DatosDeAusencia("ENFERMEDAD", HOY, null)))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessage("El tipo tiene que ser VACACIONES, FRANCO o BAJA_TEMPORAL");
        assertThatThrownBy(() -> servicio.cargarAusencia(ADMIN, juan.getId(),
                new DatosDeAusencia("FRANCO", null, null)))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessage("Falta la fecha de inicio");
        assertThatThrownBy(() -> servicio.cargarAusencia(ADMIN, juan.getId(),
                new DatosDeAusencia("FRANCO", HOY, HOY.minusDays(1))))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessage("La ausencia no puede terminar antes de empezar");

        verify(empleados, never()).guardarAusencia(any());
        verifyNoInteractions(auditor);
    }

    @Test
    void la_ausencia_de_un_dado_de_baja_da_409_y_la_de_otro_local_404() {
        DatosDeAusencia franco = new DatosDeAusencia("FRANCO", HOY, HOY);

        assertThatThrownBy(() -> servicio.cargarAusencia(ADMIN, hugo.getId(), franco))
                .isInstanceOf(ReglaDeNegocioException.class).hasMessage("El empleado está dado de baja");
        assertThatThrownBy(() -> servicio.cargarAusencia(ADMIN, OTRO_LOCAL_ID, franco))
                .isInstanceOf(NoEncontradoException.class).hasMessage("El empleado no existe");
        verify(empleados, never()).guardarAusencia(any());
        verifyNoInteractions(auditor);
    }

    @Test
    void las_ausencias_vienen_como_las_da_el_puerto_tambien_las_de_un_dado_de_baja() {
        Ausencia nueva = conId(new Ausencia(LOCAL, hugo, TipoAusencia.FRANCO, HOY, HOY, ADMIN_ID), 8L);
        Ausencia vieja = conId(new Ausencia(LOCAL, hugo, TipoAusencia.VACACIONES, HOY.minusDays(60),
                HOY.minusDays(50), ADMIN_ID), 7L);
        when(empleados.ausenciasDe(hugo.getId(), LOCAL)).thenReturn(List.of(nueva, vieja));

        assertThat(servicio.ausencias(ADMIN, hugo.getId())).extracting(AusenciaDto::id).containsExactly(8L, 7L);
        assertThatThrownBy(() -> servicio.ausencias(ADMIN, OTRO_LOCAL_ID))
                .isInstanceOf(NoEncontradoException.class).hasMessage("El empleado no existe");
    }

    @Test
    void terminar_una_ausencia_sin_fecha_de_vuelta_le_pone_el_hasta_y_se_audita() {
        Ausencia ausencia = ausenciaDeJuan(5L, HOY.minusDays(3), null);

        AusenciaDto dto = servicio.cambiarFinDeAusencia(ADMIN, juan.getId(), 5L, HOY);

        assertThat(dto.hasta()).isEqualTo(HOY);
        assertThat(ausencia.getHasta()).isEqualTo(HOY);
        verify(empleados).guardarAusencia(ausencia);
        Map<String, Object>[] valores = auditado("EDITAR_AUSENCIA", "AUSENCIA", 5L);
        assertThat(valores[0]).containsEntry("hasta", null).hasSize(1);
        assertThat(valores[1]).isEqualTo(Map.of("hasta", "2026-11-14"));
    }

    @Test
    void cambiar_la_fecha_de_vuelta_por_la_misma_no_guarda_ni_audita() {
        ausenciaDeJuan(5L, HOY, HOY.plusDays(2));

        servicio.cambiarFinDeAusencia(ADMIN, juan.getId(), 5L, HOY.plusDays(2));

        verify(empleados, never()).guardarAusencia(any());
        verifyNoInteractions(auditor);
    }

    @Test
    void una_fecha_de_fin_que_falta_o_es_anterior_al_inicio_da_400_y_no_cambia_nada() {
        Ausencia ausencia = ausenciaDeJuan(5L, HOY, HOY.plusDays(2));

        assertThatThrownBy(() -> servicio.cambiarFinDeAusencia(ADMIN, juan.getId(), 5L, null))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessage("Falta la fecha de fin");
        assertThatThrownBy(() -> servicio.cambiarFinDeAusencia(ADMIN, juan.getId(), 5L, HOY.minusDays(1)))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessage("La ausencia no puede terminar antes de empezar");

        assertThat(ausencia.getHasta()).isEqualTo(HOY.plusDays(2));
        verifyNoInteractions(auditor);
    }

    @Test
    void la_ausencia_de_otro_empleado_o_de_otro_local_da_404() {
        assertThatThrownBy(() -> servicio.cambiarFinDeAusencia(ADMIN, juan.getId(), 404L, HOY))
                .isInstanceOf(NoEncontradoException.class).hasMessage("La ausencia no existe");
        assertThatThrownBy(() -> servicio.cambiarFinDeAusencia(ADMIN, OTRO_LOCAL_ID, 5L, HOY))
                .isInstanceOf(NoEncontradoException.class).hasMessage("El empleado no existe");
        verifyNoInteractions(auditor);
    }

    @Test
    void cambiar_la_ausencia_de_un_dado_de_baja_da_409() {
        assertThatThrownBy(() -> servicio.cambiarFinDeAusencia(ADMIN, hugo.getId(), 5L, HOY))
                .isInstanceOf(ReglaDeNegocioException.class).hasMessage("El empleado está dado de baja");
        verify(empleados, never()).buscarAusencia(anyLong(), anyLong(), anyLong());
    }
}
