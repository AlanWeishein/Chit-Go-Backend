package com.chitgo.usuarios.aplicacion;

import static com.chitgo.pruebas.Entidades.conId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;

import com.chitgo.auditoria.dominio.puertos.Auditor;
import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.compartido.dominio.ReglaDeNegocioException;
import com.chitgo.compartido.dominio.RelojDePrueba;
import com.chitgo.compartido.dominio.SolicitudInvalidaException;
import com.chitgo.usuarios.aplicacion.dto.DatosDeAltaDeEmpleado;
import com.chitgo.usuarios.aplicacion.dto.DatosDeEdicionDeEmpleado;
import com.chitgo.usuarios.aplicacion.dto.EmpleadoDetalleDto;
import com.chitgo.usuarios.aplicacion.dto.EmpleadoResumenDto;
import com.chitgo.usuarios.aplicacion.dto.PaginaDto;
import com.chitgo.usuarios.dominio.Ausencia;
import com.chitgo.usuarios.dominio.DiaSemana;
import com.chitgo.usuarios.dominio.Empleado;
import com.chitgo.usuarios.dominio.MozoMesa;
import com.chitgo.usuarios.dominio.PaginaDe;
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
 * HU-14, tarea 1 de la ST-22: alta, edición, listado, detalle y baja de empleados, con mocks. Juan (id 1, Mozo)
 * y Carla (id 2, Caja) están activos en La Esquina (local 1); Hugo (id 3, Cocina) está dado de baja. Los de otro
 * local y el Admin no existen para el puerto: devuelve vacío, y el Service responde 404.
 */
class GestionarEmpleadosServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-14T15:00:00Z");
    private static final Long LOCAL = 1L;
    private static final Long ADMIN_ID = 2L;
    private static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(ADMIN_ID, "ADMIN", LOCAL, false,
            AHORA.plusSeconds(600));

    private final EmpleadoRepository empleados = mock(EmpleadoRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final RolRepository roles = mock(RolRepository.class);
    private final CodificadorDeClaves codificador = mock(CodificadorDeClaves.class);
    private final Auditor auditor = mock(Auditor.class);
    private final RelojDePrueba reloj = new RelojDePrueba(AHORA);

    private Empleado juan;
    private Empleado carla;
    private Empleado hugo;
    private GestionarEmpleadosService servicio;

    @BeforeEach
    void preparar() {
        servicio = new GestionarEmpleadosService(empleados, usuarios, roles, mock(MesasDelLocal.class), codificador,
                auditor, reloj);
        juan = empleado(1L, 10L, "MOZO", "Juan", "juan");
        carla = empleado(2L, 11L, "CAJA", "Carla", null);
        hugo = empleado(3L, 12L, "COCINA", "Hugo", null);
        hugo.darDeBaja();
        for (Empleado e : List.of(juan, carla, hugo)) {
            when(empleados.buscarPorId(e.getId(), LOCAL)).thenReturn(Optional.of(e));
            when(empleados.buscarParaModificar(e.getId(), LOCAL)).thenReturn(Optional.of(e));
        }
        for (String codigo : List.of("MOZO", "CAJA", "COCINA")) {
            when(roles.buscarPorCodigo(codigo)).thenReturn(Optional.of(new Rol(codigo, codigo)));
        }
        when(codificador.hashear(any())).thenAnswer(inv -> "hash(" + inv.getArgument(0) + ")");
        when(usuarios.guardar(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(empleados.guardar(any(Empleado.class))).thenAnswer(inv -> conId(inv.getArgument(0), 50L));
        when(empleados.turnosDe(anyCollection(), anyLong())).thenReturn(List.of());
        when(empleados.conAusenciaVigente(anyCollection(), anyLong(), any(LocalDate.class))).thenReturn(Set.of());
        when(empleados.ausenciasDe(anyLong(), anyLong())).thenReturn(List.of());
    }

    private static Empleado empleado(Long id, Long usuarioId, String codigoRol, String nombre, String nombreUsuario) {
        Usuario usuario = conId(Usuario.nuevoEmpleado(LOCAL, new Rol(codigoRol, codigoRol), nombre, nombreUsuario,
                nombreUsuario == null ? null : "hashPass", "hashPin"), usuarioId);
        return conId(new Empleado(LOCAL, usuario), id);
    }

    private static DatosDeAltaDeEmpleado pedro() {
        return new DatosDeAltaDeEmpleado("Pedro", "MOZO", "0000", "pedro", "Clave2026!");
    }

    private static String mensaje(Class<? extends RuntimeException> tipo, Executable accion) {
        return assertThrows(tipo, accion).getMessage();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object>[] auditado(String accion, Long empleadoId) {
        ArgumentCaptor<Object> antes = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> despues = ArgumentCaptor.forClass(Object.class);
        verify(auditor).registrar(eq(LOCAL), eq(ADMIN_ID), eq(accion), eq("EMPLEADO"), eq(empleadoId),
                antes.capture(), despues.capture());
        return new Map[] {(Map<String, Object>) antes.getValue(), (Map<String, Object>) despues.getValue()};
    }

    private static void sinSecretos(Map<String, Object> valores) {
        String texto = valores.toString().toLowerCase();
        for (String secreto : List.of("0000", "clave2026", "hash", "pin", "password", "contrasena")) {
            assertFalse(texto.contains(secreto), "La auditoría no debe llevar '" + secreto + "': " + texto);
        }
    }

    // --- Alta

    @Test
    void CP_27_01_el_alta_de_un_mozo_guarda_usuario_y_empleado_con_las_marcas_y_responde_el_detalle() {
        EmpleadoDetalleDto dto = servicio.crear(ADMIN, pedro());

        ArgumentCaptor<Usuario> usuario = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).guardar(usuario.capture());
        Usuario guardado = usuario.getValue();
        assertEquals(LOCAL, guardado.getLocalId());
        assertEquals("MOZO", guardado.getRol().getCodigo());
        assertEquals("Pedro", guardado.getNombre());
        assertEquals("pedro", guardado.getNombreUsuario());
        assertTrue(guardado.isDebeCambiarCredencial());
        assertTrue(guardado.isContrasenaProvisoria());
        assertTrue(guardado.isActivo());
        ArgumentCaptor<Empleado> empleado = ArgumentCaptor.forClass(Empleado.class);
        verify(empleados).guardar(empleado.capture());
        assertEquals(LOCAL, empleado.getValue().getLocalId());
        assertEquals(50L, dto.id());
        assertEquals("Pedro", dto.nombre());
        assertEquals("MOZO", dto.rol());
        assertTrue(dto.activo());
        assertTrue(dto.debeCambiarCredencial());
        assertTrue(dto.contrasenaProvisoria());
    }

    @Test
    void el_pin_y_la_contrasena_se_guardan_hasheados_nunca_en_claro() {
        servicio.crear(ADMIN, pedro());

        ArgumentCaptor<Usuario> usuario = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).guardar(usuario.capture());
        assertEquals("hash(0000)", usuario.getValue().getPinHash());
        assertEquals("hash(Clave2026!)", usuario.getValue().getHashPassword());
    }

    @Test
    void el_alta_de_caja_y_de_cocina_no_lleva_usuario_ni_contrasena_y_no_queda_provisoria() {
        for (String rol : List.of("CAJA", "COCINA")) {
            EmpleadoDetalleDto dto = servicio.crear(ADMIN, new DatosDeAltaDeEmpleado("Nuevo", rol, "1111", null, null));

            assertEquals(rol, dto.rol());
            assertTrue(dto.debeCambiarCredencial(), rol);
            assertFalse(dto.contrasenaProvisoria(), rol);
            assertNull(dto.nombreUsuario(), rol);
        }
    }

    @Test
    void el_rol_se_acepta_sin_distinguir_mayusculas_y_el_nombre_se_recorta() {
        EmpleadoDetalleDto dto = servicio.crear(ADMIN,
                new DatosDeAltaDeEmpleado("  Pedro  ", "mozo", "0000", " pedro ", "Clave2026!"));

        assertEquals("MOZO", dto.rol());
        assertEquals("Pedro", dto.nombre());
        assertEquals("pedro", dto.nombreUsuario());
    }

    @Test
    void el_alta_audita_crear_empleado_sin_valor_anterior_y_sin_pin_contrasena_ni_hash() {
        servicio.crear(ADMIN, pedro());

        Map<String, Object>[] valores = auditado("CREAR_EMPLEADO", 50L);
        assertNull(valores[0]);
        assertEquals("Pedro", valores[1].get("nombre"));
        assertEquals("pedro", valores[1].get("nombreUsuario"));
        assertEquals("MOZO", valores[1].get("rol"));
        assertEquals(true, valores[1].get("activo"));
        sinSecretos(valores[1]);
    }

    @Test
    void falta_el_nombre_o_el_rol_es_400_con_el_campo_marcado_y_no_guarda() {
        assertEquals("Falta el nombre", mensaje(SolicitudInvalidaException.class,
                () -> servicio.crear(ADMIN, new DatosDeAltaDeEmpleado(null, "MOZO", "0000", "pedro", "Clave2026!"))));
        assertEquals("Falta el nombre", mensaje(SolicitudInvalidaException.class,
                () -> servicio.crear(ADMIN, new DatosDeAltaDeEmpleado("  ", "CAJA", "0000", null, null))));
        assertEquals("Falta el rol", mensaje(SolicitudInvalidaException.class,
                () -> servicio.crear(ADMIN, new DatosDeAltaDeEmpleado("Pedro", null, "0000", null, null))));
        assertEquals("Falta el rol", mensaje(SolicitudInvalidaException.class,
                () -> servicio.crear(ADMIN, new DatosDeAltaDeEmpleado("Pedro", " ", "0000", null, null))));

        verify(usuarios, never()).guardar(any());
        verify(empleados, never()).guardar(any());
        verifyNoInteractions(auditor);
    }

    @Test
    void solo_se_dan_de_alta_mozo_caja_y_cocina_admin_gerente_y_los_inventados_son_400() {
        for (String rol : List.of("ADMIN", "GERENTE_DUENO", "Gerente", "DUENO", "pirata")) {
            assertEquals("El rol tiene que ser Mozo, Caja o Cocina", mensaje(SolicitudInvalidaException.class,
                    () -> servicio.crear(ADMIN, new DatosDeAltaDeEmpleado("Pedro", rol, "0000", null, null))), rol);
        }

        verify(usuarios, never()).guardar(any());
        verifyNoInteractions(auditor);
    }

    @Test
    void falta_el_pin_es_400() {
        assertEquals("Falta el PIN", mensaje(SolicitudInvalidaException.class,
                () -> servicio.crear(ADMIN, new DatosDeAltaDeEmpleado("Pedro", "CAJA", null, null, null))));
        assertEquals("Falta el PIN", mensaje(SolicitudInvalidaException.class,
                () -> servicio.crear(ADMIN, new DatosDeAltaDeEmpleado("Pedro", "CAJA", " ", null, null))));
    }

    @Test
    void un_mozo_sin_usuario_o_sin_contrasena_es_400() {
        assertEquals("Falta el usuario del mozo", mensaje(SolicitudInvalidaException.class,
                () -> servicio.crear(ADMIN, new DatosDeAltaDeEmpleado("Pedro", "MOZO", "0000", null, "Clave2026!"))));
        assertEquals("Falta el usuario del mozo", mensaje(SolicitudInvalidaException.class,
                () -> servicio.crear(ADMIN, new DatosDeAltaDeEmpleado("Pedro", "MOZO", "0000", "  ", "Clave2026!"))));
        assertEquals("Falta la contraseña del mozo", mensaje(SolicitudInvalidaException.class,
                () -> servicio.crear(ADMIN, new DatosDeAltaDeEmpleado("Pedro", "MOZO", "0000", "pedro", null))));

        verify(usuarios, never()).guardar(any());
    }

    @Test
    void caja_o_cocina_con_usuario_o_contrasena_es_400() {
        assertEquals("Solo el mozo lleva usuario y contraseña", mensaje(SolicitudInvalidaException.class,
                () -> servicio.crear(ADMIN, new DatosDeAltaDeEmpleado("Pedro", "CAJA", "0000", "pedro", null))));
        assertEquals("Solo el mozo lleva usuario y contraseña", mensaje(SolicitudInvalidaException.class,
                () -> servicio.crear(ADMIN, new DatosDeAltaDeEmpleado("Pedro", "COCINA", "0000", null, "Clave2026!"))));

        verify(usuarios, never()).guardar(any());
        verifyNoInteractions(auditor);
    }

    @Test
    void CP_27_03_el_usuario_repetido_en_el_local_es_409_y_no_guarda_ni_audita() {
        when(usuarios.existeNombreDeUsuario(LOCAL, "juan", null)).thenReturn(true);

        assertEquals("Ya existe un usuario con ese nombre", mensaje(ReglaDeNegocioException.class,
                () -> servicio.crear(ADMIN, new DatosDeAltaDeEmpleado("Otro Juan", "MOZO", "0000", "juan",
                        "Clave2026!"))));

        verify(usuarios, never()).guardar(any());
        verify(empleados, never()).guardar(any());
        verifyNoInteractions(auditor);
    }

    @Test
    void CP_27_03_el_service_pregunta_por_el_usuario_en_el_local_de_la_sesion_sin_excluir_a_nadie() {
        servicio.crear(ADMIN, new DatosDeAltaDeEmpleado("Otro Juan", "MOZO", "0000", "JUAN", "Clave2026!"));

        // Que "JUAN" choque con "juan" y que El Puerto no cuente es del puerto: lo prueba el test contra MySQL.
        verify(usuarios).existeNombreDeUsuario(LOCAL, "JUAN", null);
    }

    @Test
    void un_usuario_sin_local_no_gestiona_empleados() {
        UsuarioAutenticado gerente = new UsuarioAutenticado(9L, "GERENTE_DUENO", null, false, AHORA.plusSeconds(600));

        assertThrows(AccesoDenegadoException.class, () -> servicio.crear(gerente, pedro()));
        assertThrows(AccesoDenegadoException.class, () -> servicio.listar(gerente, 0, 20));
        assertThrows(AccesoDenegadoException.class, () -> servicio.detalle(gerente, 1L));
        assertThrows(AccesoDenegadoException.class,
                () -> servicio.editar(gerente, 1L, new DatosDeEdicionDeEmpleado("X", "x")));
        assertThrows(AccesoDenegadoException.class, () -> servicio.darDeBaja(gerente, 1L));
        verifyNoInteractions(auditor);
    }

    // --- Edición

    @Test
    void editar_cambia_el_nombre_y_audita_solo_lo_que_cambio() {
        EmpleadoDetalleDto dto = servicio.editar(ADMIN, 1L, new DatosDeEdicionDeEmpleado("Juan Pablo", "juan"));

        assertEquals("Juan Pablo", dto.nombre());
        assertEquals("juan", dto.nombreUsuario());
        verify(usuarios).guardar(juan.getUsuario());
        Map<String, Object>[] valores = auditado("EDITAR_EMPLEADO", 1L);
        assertEquals(Map.of("nombre", "Juan"), valores[0]);
        assertEquals(Map.of("nombre", "Juan Pablo"), valores[1]);
    }

    @Test
    void editar_el_usuario_del_mozo_audita_el_anterior_y_el_nuevo() {
        servicio.editar(ADMIN, 1L, new DatosDeEdicionDeEmpleado("Juan", "juanpa"));

        Map<String, Object>[] valores = auditado("EDITAR_EMPLEADO", 1L);
        assertEquals(Map.of("nombreUsuario", "juan"), valores[0]);
        assertEquals(Map.of("nombreUsuario", "juanpa"), valores[1]);
        sinSecretos(valores[1]);
    }

    @Test
    void editar_sin_cambios_no_audita_y_no_guarda() {
        servicio.editar(ADMIN, 1L, new DatosDeEdicionDeEmpleado("Juan", "juan"));

        verifyNoInteractions(auditor);
        verify(usuarios, never()).guardar(any());
    }

    @Test
    void editar_a_caja_cambia_el_nombre_y_no_tiene_usuario() {
        EmpleadoDetalleDto dto = servicio.editar(ADMIN, 2L, new DatosDeEdicionDeEmpleado("Carla M.", null));

        assertEquals("Carla M.", dto.nombre());
        assertNull(dto.nombreUsuario());
        assertEquals("CAJA", dto.rol());
    }

    @Test
    void editar_no_cambia_el_rol_ni_las_credenciales() {
        servicio.editar(ADMIN, 1L, new DatosDeEdicionDeEmpleado("Juan Pablo", "juanpa"));

        assertEquals("MOZO", juan.getUsuario().getRol().getCodigo());
        assertEquals("hashPin", juan.getUsuario().getPinHash());
        assertEquals("hashPass", juan.getUsuario().getHashPassword());
        verifyNoInteractions(codificador);
    }

    @Test
    void editar_con_el_usuario_de_otro_es_409_y_con_el_propio_se_excluye_a_si_mismo() {
        when(usuarios.existeNombreDeUsuario(LOCAL, "ana", 10L)).thenReturn(true);

        assertEquals("Ya existe un usuario con ese nombre", mensaje(ReglaDeNegocioException.class,
                () -> servicio.editar(ADMIN, 1L, new DatosDeEdicionDeEmpleado("Juan", "ana"))));
        assertEquals("juan", juan.getUsuario().getNombreUsuario());
        verifyNoInteractions(auditor);

        servicio.editar(ADMIN, 1L, new DatosDeEdicionDeEmpleado("Juan", "juan"));
        verify(usuarios).existeNombreDeUsuario(LOCAL, "juan", 10L);
    }

    @Test
    void editar_a_un_dado_de_baja_es_409_no_cambia_nada_y_no_audita() {
        assertEquals("El empleado está dado de baja", mensaje(ReglaDeNegocioException.class,
                () -> servicio.editar(ADMIN, 3L, new DatosDeEdicionDeEmpleado("Hugo Nuevo", null))));

        assertEquals("Hugo", hugo.getUsuario().getNombre());
        verify(usuarios, never()).guardar(any());
        verifyNoInteractions(auditor);
    }

    @Test
    void editar_a_un_dado_de_baja_responde_409_aunque_el_cuerpo_este_mal() {
        assertEquals("El empleado está dado de baja", mensaje(ReglaDeNegocioException.class,
                () -> servicio.editar(ADMIN, 3L, new DatosDeEdicionDeEmpleado(null, null))));
    }

    @Test
    void editar_sin_nombre_es_400() {
        assertEquals("Falta el nombre", mensaje(SolicitudInvalidaException.class,
                () -> servicio.editar(ADMIN, 1L, new DatosDeEdicionDeEmpleado(" ", "juan"))));
        verifyNoInteractions(auditor);
    }

    @Test
    void editar_a_un_mozo_sin_usuario_es_400_y_a_caja_con_usuario_tambien() {
        assertEquals("Falta el usuario del mozo", mensaje(SolicitudInvalidaException.class,
                () -> servicio.editar(ADMIN, 1L, new DatosDeEdicionDeEmpleado("Juan", null))));
        assertEquals("Solo el mozo lleva usuario", mensaje(SolicitudInvalidaException.class,
                () -> servicio.editar(ADMIN, 2L, new DatosDeEdicionDeEmpleado("Carla", "carla"))));
    }

    @Test
    void editar_a_alguien_que_no_existe_de_otro_local_o_el_admin_es_404_sin_nombrar_otro_local() {
        // El puerto devuelve vacío para el de El Puerto y para el Admin: no los ve.
        assertEquals("El empleado no existe", mensaje(NoEncontradoException.class,
                () -> servicio.editar(ADMIN, 99L, new DatosDeEdicionDeEmpleado("X", "x"))));

        verify(usuarios, never()).guardar(any());
        verifyNoInteractions(auditor);
    }

    // --- Baja

    @Test
    void CP_27_04_la_baja_es_logica_desactiva_al_usuario_y_audita_baja_empleado() {
        servicio.darDeBaja(ADMIN, 1L);

        assertFalse(juan.estaActivo());
        verify(usuarios).guardar(juan.getUsuario());
        verify(empleados, never()).guardar(any());
        Map<String, Object>[] valores = auditado("BAJA_EMPLEADO", 1L);
        assertEquals(Map.of("activo", true), valores[0]);
        assertEquals(Map.of("activo", false), valores[1]);
    }

    @Test
    void la_baja_no_quita_las_mesas_del_mozo() {
        Empleado conMesas = empleado(4L, 13L, "MOZO", "Pedro", "pedro");
        new MozoMesa(LOCAL, conMesas, 5L);
        when(empleados.buscarParaModificar(4L, LOCAL)).thenReturn(Optional.of(conMesas));

        servicio.darDeBaja(ADMIN, 4L);

        assertEquals(Set.of(5L), conMesas.getMesaIds());
    }

    @Test
    void darlo_de_baja_dos_veces_no_falla_y_audita_una_sola_vez() {
        servicio.darDeBaja(ADMIN, 1L);
        servicio.darDeBaja(ADMIN, 1L);

        verify(auditor).registrar(eq(LOCAL), eq(ADMIN_ID), eq("BAJA_EMPLEADO"), eq("EMPLEADO"), eq(1L), any(), any());
        verify(usuarios).guardar(juan.getUsuario());
    }

    @Test
    void dar_de_baja_a_uno_ya_dado_de_baja_no_audita() {
        servicio.darDeBaja(ADMIN, 3L);

        verifyNoInteractions(auditor);
        verify(usuarios, never()).guardar(any());
    }

    @Test
    void dar_de_baja_a_alguien_que_no_existe_de_otro_local_o_el_admin_es_404() {
        assertEquals("El empleado no existe", mensaje(NoEncontradoException.class,
                () -> servicio.darDeBaja(ADMIN, 99L)));

        verify(usuarios, never()).guardar(any());
        verifyNoInteractions(auditor);
    }

    // --- Detalle

    @Test
    void el_detalle_trae_turnos_ordenados_mesas_y_ausencias() {
        Turno martes = new Turno(LOCAL, juan, DiaSemana.MAR, LocalTime.of(12, 0), LocalTime.of(16, 0));
        Turno lunes = new Turno(LOCAL, juan, DiaSemana.LUN, LocalTime.of(20, 0), LocalTime.of(2, 0));
        new MozoMesa(LOCAL, juan, 3L);
        new MozoMesa(LOCAL, juan, 1L);
        Ausencia pasada = conId(new Ausencia(LOCAL, juan, TipoAusencia.FRANCO, LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 2), ADMIN_ID), 7L);
        when(empleados.turnosDe(List.of(1L), LOCAL)).thenReturn(List.of(martes, lunes));
        when(empleados.ausenciasDe(1L, LOCAL)).thenReturn(List.of(pasada));

        EmpleadoDetalleDto dto = servicio.detalle(ADMIN, 1L);

        assertEquals(List.of(DiaSemana.LUN, DiaSemana.MAR), dto.turnos().stream().map(t -> t.dia()).toList());
        assertEquals(LocalTime.of(2, 0), dto.turnos().get(0).horaFin());
        assertEquals(List.of(1L, 3L), dto.mesaIds());
        assertEquals(1, dto.ausencias().size());
        assertEquals(7L, dto.ausencias().get(0).id());
        assertFalse(dto.ausenciaVigente());
    }

    @Test
    void el_detalle_marca_la_ausencia_vigente_hoy_con_la_fecha_del_local() {
        // 2026-11-14T15:00Z son las 12:00 del 14/11 en Buenos Aires.
        Ausencia vacaciones = conId(new Ausencia(LOCAL, juan, TipoAusencia.VACACIONES, LocalDate.of(2026, 11, 14),
                LocalDate.of(2026, 11, 14), ADMIN_ID), 8L);
        when(empleados.ausenciasDe(1L, LOCAL)).thenReturn(List.of(vacaciones));

        assertTrue(servicio.detalle(ADMIN, 1L).ausenciaVigente());
    }

    @Test
    void el_detalle_de_alguien_que_no_existe_de_otro_local_o_el_admin_es_404() {
        assertEquals("El empleado no existe", mensaje(NoEncontradoException.class, () -> servicio.detalle(ADMIN, 99L)));
    }

    @Test
    void el_detalle_de_un_dado_de_baja_se_puede_ver_marcado() {
        assertFalse(servicio.detalle(ADMIN, 3L).activo());
    }

    @Test
    void el_detalle_marca_el_bloqueo() {
        for (int i = 0; i < Usuario.INTENTOS_PARA_BLOQUEAR; i++) {
            juan.getUsuario().registrarIntentoFallido();
        }

        assertTrue(servicio.detalle(ADMIN, 1L).bloqueado());
        assertFalse(servicio.detalle(ADMIN, 2L).bloqueado());
    }

    // --- Listado

    @Test
    void listar_arma_la_pagina_con_el_total_y_pide_al_puerto_el_local_de_la_sesion() {
        when(empleados.listarPagina(LOCAL, 0, 2)).thenReturn(new PaginaDe<>(List.of(carla, hugo), 5));

        PaginaDto<EmpleadoResumenDto> pagina = servicio.listar(ADMIN, 0, 2);

        assertEquals(0, pagina.page());
        assertEquals(2, pagina.size());
        assertEquals(5, pagina.totalElementos());
        assertEquals(3, pagina.totalPaginas());
        assertEquals(List.of("Carla", "Hugo"), pagina.contenido().stream().map(EmpleadoResumenDto::nombre).toList());
        assertEquals(List.of("CAJA", "COCINA"), pagina.contenido().stream().map(EmpleadoResumenDto::rol).toList());
    }

    @Test
    void listar_marca_a_los_dados_de_baja_pero_los_incluye() {
        when(empleados.listarPagina(LOCAL, 0, 20)).thenReturn(new PaginaDe<>(List.of(carla, hugo), 2));

        List<EmpleadoResumenDto> filas = servicio.listar(ADMIN, 0, 20).contenido();

        assertTrue(filas.get(0).activo());
        assertFalse(filas.get(1).activo());
    }

    @Test
    void listar_una_pagina_vacia_mas_alla_del_final_conserva_el_total() {
        when(empleados.listarPagina(LOCAL, 3, 20)).thenReturn(new PaginaDe<>(List.of(), 45));

        PaginaDto<EmpleadoResumenDto> pagina = servicio.listar(ADMIN, 3, 20);

        assertTrue(pagina.contenido().isEmpty());
        assertEquals(45, pagina.totalElementos());
        assertEquals(3, pagina.totalPaginas());
    }

    @Test
    void listar_sin_empleados_tiene_cero_paginas() {
        when(empleados.listarPagina(LOCAL, 0, 20)).thenReturn(new PaginaDe<>(List.of(), 0));

        assertEquals(0, servicio.listar(ADMIN, 0, 20).totalPaginas());
    }

    @Test
    void listar_el_total_justo_en_el_limite_no_suma_una_pagina_de_mas() {
        when(empleados.listarPagina(LOCAL, 0, 20)).thenReturn(new PaginaDe<>(List.of(carla), 20));
        assertEquals(1, servicio.listar(ADMIN, 0, 20).totalPaginas());

        when(empleados.listarPagina(LOCAL, 0, 20)).thenReturn(new PaginaDe<>(List.of(carla), 21));
        assertEquals(2, servicio.listar(ADMIN, 0, 20).totalPaginas());
    }

    @Test
    void listar_trae_turnos_y_ausencia_vigente_de_cada_uno_con_una_consulta_para_toda_la_pagina() {
        Turno turnoJuan = new Turno(LOCAL, juan, DiaSemana.LUN, LocalTime.of(12, 0), LocalTime.of(16, 0));
        when(empleados.listarPagina(LOCAL, 0, 20)).thenReturn(new PaginaDe<>(List.of(juan, carla), 2));
        when(empleados.turnosDe(List.of(1L, 2L), LOCAL)).thenReturn(List.of(turnoJuan));
        when(empleados.conAusenciaVigente(List.of(1L, 2L), LOCAL, LocalDate.of(2026, 11, 14))).thenReturn(Set.of(2L));

        List<EmpleadoResumenDto> filas = servicio.listar(ADMIN, 0, 20).contenido();

        assertEquals(1, filas.get(0).turnos().size());
        assertEquals(DiaSemana.LUN, filas.get(0).turnos().get(0).dia());
        assertTrue(filas.get(1).turnos().isEmpty());
        assertFalse(filas.get(0).ausenciaVigente());
        assertTrue(filas.get(1).ausenciaVigente());
        verify(empleados).turnosDe(anyCollection(), eq(LOCAL));
        verify(empleados).conAusenciaVigente(anyCollection(), eq(LOCAL), any(LocalDate.class));
    }

    @Test
    void listar_con_page_o_size_fuera_de_rango_es_400_y_no_consulta() {
        assertEquals("La página o el tamaño no son válidos",
                mensaje(SolicitudInvalidaException.class, () -> servicio.listar(ADMIN, -1, 20)));
        assertEquals("La página o el tamaño no son válidos",
                mensaje(SolicitudInvalidaException.class, () -> servicio.listar(ADMIN, 0, 0)));
        assertEquals("La página o el tamaño no son válidos",
                mensaje(SolicitudInvalidaException.class, () -> servicio.listar(ADMIN, 0, 101)));

        verify(empleados, never()).listarPagina(anyLong(), anyInt(), anyInt());
    }

    @Test
    void listar_acepta_los_bordes_size_1_y_size_100_y_page_0() {
        when(empleados.listarPagina(any(), anyInt(), anyInt())).thenReturn(new PaginaDe<>(List.of(), 0));

        assertEquals(1, servicio.listar(ADMIN, 0, 1).size());
        assertEquals(100, servicio.listar(ADMIN, 0, 100).size());
    }

    @Test
    void listar_no_toca_la_auditoria() {
        when(empleados.listarPagina(LOCAL, 0, 20)).thenReturn(new PaginaDe<>(List.of(juan), 1));

        servicio.listar(ADMIN, 0, 20);

        verifyNoInteractions(auditor);
    }
}
