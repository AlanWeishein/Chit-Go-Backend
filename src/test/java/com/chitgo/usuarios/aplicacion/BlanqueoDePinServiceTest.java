package com.chitgo.usuarios.aplicacion;

import static com.chitgo.pruebas.Entidades.conId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.chitgo.auditoria.dominio.puertos.Auditor;
import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.compartido.dominio.ReglaDeNegocioException;
import com.chitgo.compartido.dominio.RelojDePrueba;
import com.chitgo.compartido.dominio.SolicitudInvalidaException;
import com.chitgo.usuarios.aplicacion.dto.EmpleadoDetalleDto;
import com.chitgo.usuarios.dominio.Empleado;
import com.chitgo.usuarios.dominio.Rol;
import com.chitgo.usuarios.dominio.Usuario;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;
import com.chitgo.usuarios.dominio.puertos.CodificadorDeClaves;
import com.chitgo.usuarios.dominio.puertos.EmpleadoRepository;
import com.chitgo.usuarios.dominio.puertos.MesasDelLocal;
import com.chitgo.usuarios.dominio.puertos.RolRepository;
import com.chitgo.usuarios.dominio.puertos.UsuarioRepository;

/**
 * HU-14, tarea 3 de la ST-22: POST /admin/empleados/{id}/pin, con mocks. Juan (empleado 1, Mozo) ya eligió sus
 * credenciales y está bloqueado; Pedro (empleado 2, Mozo) es nuevo y tiene la contraseña provisoria; Hugo
 * (empleado 3, Cocina) está dado de baja. El resto, para el puerto, no existe.
 */
class BlanqueoDePinServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-14T15:00:00Z");
    private static final Long LOCAL = 1L;
    private static final Long ADMIN_ID = 2L;
    private static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(ADMIN_ID, "ADMIN", LOCAL, false,
            AHORA.plusSeconds(600));

    private final EmpleadoRepository empleados = mock(EmpleadoRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final CodificadorDeClaves codificador = mock(CodificadorDeClaves.class);
    private final Auditor auditor = mock(Auditor.class);

    private Empleado juan;
    private Empleado pedro;
    private Empleado hugo;
    private GestionarEmpleadosService servicio;

    @BeforeEach
    void preparar() {
        servicio = new GestionarEmpleadosService(empleados, usuarios, mock(RolRepository.class),
                mock(MesasDelLocal.class), codificador, auditor, new RelojDePrueba(AHORA));
        juan = empleado(1L, 10L, "MOZO", "Juan", "juan");
        juan.getUsuario().elegirCredencial("pinElegido", "passElegida");
        for (int i = 0; i < Usuario.INTENTOS_PARA_BLOQUEAR; i++) {
            juan.getUsuario().registrarIntentoFallido();
        }
        pedro = empleado(2L, 11L, "MOZO", "Pedro", "pedro");
        hugo = empleado(3L, 12L, "COCINA", "Hugo", null);
        hugo.darDeBaja();
        for (Empleado e : List.of(juan, pedro, hugo)) {
            when(empleados.buscarParaModificar(e.getId(), LOCAL)).thenReturn(Optional.of(e));
        }
        when(codificador.hashear(any())).thenAnswer(inv -> "hash(" + inv.getArgument(0) + ")");
        when(usuarios.guardar(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(empleados.turnosDe(anyCollection(), anyLong())).thenReturn(List.of());
        when(empleados.ausenciasDe(anyLong(), anyLong())).thenReturn(List.of());
    }

    private static Empleado empleado(Long id, Long usuarioId, String codigoRol, String nombre, String nombreUsuario) {
        Usuario usuario = conId(Usuario.nuevoEmpleado(LOCAL, new Rol(codigoRol, codigoRol), nombre, nombreUsuario,
                nombreUsuario == null ? null : "hashPass", "hashPin"), usuarioId);
        return conId(new Empleado(LOCAL, usuario), id);
    }

    @Test
    void CP_10_06_el_blanqueo_destraba_al_bloqueado_y_deja_el_pin_provisorio() {
        EmpleadoDetalleDto detalle = servicio.blanquearPin(ADMIN, 1L, "0000");

        Usuario u = juan.getUsuario();
        assertEquals("hash(0000)", u.getPinHash());
        assertEquals(0, u.getIntentosFallidos());
        assertNull(u.getBloqueadoHasta());
        assertTrue(u.isDebeCambiarCredencial());
        assertFalse(detalle.bloqueado());
        assertTrue(detalle.debeCambiarCredencial());
        verify(usuarios).guardar(u);
    }

    @Test
    void el_blanqueo_no_le_vuelve_a_pedir_la_contrasena_al_mozo_que_ya_la_eligio() {
        servicio.blanquearPin(ADMIN, 1L, "0000");

        assertFalse(juan.getUsuario().isContrasenaProvisoria());
        assertEquals("passElegida", juan.getUsuario().getHashPassword());
    }

    @Test
    void el_blanqueo_deja_la_contrasena_provisoria_del_mozo_nuevo_como_estaba() {
        EmpleadoDetalleDto detalle = servicio.blanquearPin(ADMIN, 2L, "0000");

        assertTrue(pedro.getUsuario().isContrasenaProvisoria());
        assertTrue(detalle.contrasenaProvisoria());
    }

    @Test
    void el_blanqueo_audita_sin_el_pin_ni_un_hash() {
        servicio.blanquearPin(ADMIN, 1L, "0000");

        ArgumentCaptor<Object> antes = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> despues = ArgumentCaptor.forClass(Object.class);
        verify(auditor).registrar(eq(LOCAL), eq(ADMIN_ID), eq("BLANQUEAR_PIN"), eq("EMPLEADO"), eq(1L),
                antes.capture(), despues.capture());
        assertEquals(Map.of("intentosFallidos", 5, "bloqueado", true, "debeCambiarCredencial", false),
                antes.getValue());
        assertEquals(Map.of("intentosFallidos", 0, "bloqueado", false, "debeCambiarCredencial", true),
                despues.getValue());
        String texto = (antes.getValue().toString() + despues.getValue()).toLowerCase();
        for (String secreto : List.of("0000", "hash", "pinelegido", "passelegida")) {
            assertFalse(texto.contains(secreto), "La auditoría no debe llevar '" + secreto + "': " + texto);
        }
    }

    @Test
    void blanquear_dos_veces_audita_las_dos() {
        servicio.blanquearPin(ADMIN, 2L, "0000");
        servicio.blanquearPin(ADMIN, 2L, "1111");

        assertEquals("hash(1111)", pedro.getUsuario().getPinHash());
        verify(auditor, times(2)).registrar(eq(LOCAL), eq(ADMIN_ID), eq("BLANQUEAR_PIN"),
                eq("EMPLEADO"), eq(2L), any(), any());
    }

    @Test
    void blanquear_a_un_dado_de_baja_es_409_y_no_cambia_nada() {
        String pinAntes = hugo.getUsuario().getPinHash();

        ReglaDeNegocioException e = assertThrows(ReglaDeNegocioException.class,
                () -> servicio.blanquearPin(ADMIN, 3L, "0000"));

        assertEquals("El empleado está dado de baja", e.getMessage());
        assertEquals(pinAntes, hugo.getUsuario().getPinHash());
        verify(usuarios, never()).guardar(any());
        verifyNoInteractions(auditor);
    }

    @Test
    void blanquear_a_alguien_que_no_existe_de_otro_local_o_el_admin_es_404() {
        NoEncontradoException e = assertThrows(NoEncontradoException.class,
                () -> servicio.blanquearPin(ADMIN, 99L, "0000"));

        assertEquals("El empleado no existe", e.getMessage());
        verify(usuarios, never()).guardar(any());
        verifyNoInteractions(auditor);
    }

    @Test
    void sin_pin_es_400_y_no_cambia_nada() {
        for (String pin : new String[] {null, " "}) {
            SolicitudInvalidaException e = assertThrows(SolicitudInvalidaException.class,
                    () -> servicio.blanquearPin(ADMIN, 1L, pin));
            assertEquals("Falta el PIN", e.getMessage());
        }
        assertTrue(juan.getUsuario().estaBloqueado(AHORA));
        verify(usuarios, never()).guardar(any());
        verifyNoInteractions(auditor);
    }

    @Test
    void con_un_dado_de_baja_el_409_sale_antes_que_el_400() {
        assertThrows(ReglaDeNegocioException.class, () -> servicio.blanquearPin(ADMIN, 3L, null));
    }
}
