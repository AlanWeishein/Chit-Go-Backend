package com.chitgo.usuarios.aplicacion;

import static com.chitgo.pruebas.Entidades.conId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import com.chitgo.usuarios.aplicacion.dto.AdminDto;
import com.chitgo.usuarios.aplicacion.dto.DatosDeAltaDeAdmin;
import com.chitgo.usuarios.dominio.Empleado;
import com.chitgo.usuarios.dominio.Local;
import com.chitgo.usuarios.dominio.Rol;
import com.chitgo.usuarios.dominio.Usuario;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;
import com.chitgo.usuarios.dominio.puertos.CodificadorDeClaves;
import com.chitgo.usuarios.dominio.puertos.EmpleadoRepository;
import com.chitgo.usuarios.dominio.puertos.LocalRepository;
import com.chitgo.usuarios.dominio.puertos.RolRepository;
import com.chitgo.usuarios.dominio.puertos.UsuarioRepository;

/**
 * HU-16, tarea 1 de la ST-24, con mocks. El Gerente/Dueño (usuario 1) es propietario de La Esquina (local 7). Ana
 * (usuario 20) es Admin activo de La Esquina. Un id de otro rol o de otro local no existe para el puerto: devuelve
 * vacío, y el Service responde 404.
 */
class GestionarAdminsServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-16T15:00:00Z");
    private static final Long LOCAL = 7L;
    private static final UsuarioAutenticado GERENTE = new UsuarioAutenticado(1L, "GERENTE_DUENO", null, false,
            AHORA.plusSeconds(600));
    private static final UsuarioAutenticado SIN_LOCAL = new UsuarioAutenticado(2L, "GERENTE_DUENO", null, false,
            AHORA.plusSeconds(600));

    private final LocalRepository locales = mock(LocalRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final EmpleadoRepository empleados = mock(EmpleadoRepository.class);
    private final RolRepository roles = mock(RolRepository.class);
    private final CodificadorDeClaves codificador = mock(CodificadorDeClaves.class);
    private final Auditor auditor = mock(Auditor.class);
    private final RelojDePrueba reloj = new RelojDePrueba(AHORA);

    private Usuario ana;
    private GestionarAdminsService servicio;

    @BeforeEach
    void preparar() {
        servicio = new GestionarAdminsService(locales, usuarios, empleados, roles, codificador, auditor, reloj);
        Local esquina = conId(new Local(1L, "La Esquina", null, "token"), LOCAL);
        when(locales.buscarPorPropietario(1L)).thenReturn(Optional.of(esquina));
        when(locales.buscarPorPropietario(2L)).thenReturn(Optional.empty());
        when(roles.buscarPorCodigo("ADMIN")).thenReturn(Optional.of(new Rol("ADMIN", "Admin")));
        when(codificador.hashear(any())).thenAnswer(inv -> "hash(" + inv.getArgument(0) + ")");
        when(usuarios.guardar(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarios.guardarConEmail(any(Usuario.class))).thenAnswer(inv -> conId(inv.getArgument(0), 30L));
        when(empleados.guardar(any(Empleado.class))).thenAnswer(inv -> inv.getArgument(0));

        ana = admin(20L, "Ana", "ana@laesquina.test");
        when(usuarios.buscarAdminParaModificar(20L, LOCAL)).thenReturn(Optional.of(ana));
        when(usuarios.buscarAdminParaModificar(eq(99L), anyLong())).thenReturn(Optional.empty());
    }

    private static Usuario admin(Long id, String nombre, String email) {
        Usuario u = Usuario.nuevoAdmin(LOCAL, new Rol("ADMIN", "Admin"), nombre, email, "hash");
        u.elegirCredencial(null, "hash propio");
        return conId(u, id);
    }

    // --- El local

    @Test
    void elLocalSaleDelPropietarioYNoDelPedido() {
        when(usuarios.listarAdminsDelLocal(LOCAL)).thenReturn(List.of(ana));

        List<AdminDto> lista = servicio.listar(GERENTE);

        assertEquals(List.of(new AdminDto(20L, "Ana", "ana@laesquina.test", true, false)), lista);
        verify(usuarios).listarAdminsDelLocal(LOCAL);
    }

    @Test
    void sinUnLocalDelQueSeaPropietarioDa404() {
        NoEncontradoException e = assertThrows(NoEncontradoException.class, () -> servicio.listar(SIN_LOCAL));
        assertEquals("El local no existe", e.getMessage());
        assertThrows(NoEncontradoException.class,
                () -> servicio.crear(SIN_LOCAL, new DatosDeAltaDeAdmin("Bea", "bea@x.test", "Provisoria1")));
        verifyNoInteractions(auditor);
    }

    // --- El alta

    @Test
    void creaElAdminConLaMarcaDeCambioYSuFilaDeEmpleado() {
        // CP-31-01
        AdminDto creado = servicio.crear(GERENTE, new DatosDeAltaDeAdmin(" Bea ", " bea@laesquina.test ", "Provisoria1"));

        assertEquals(new AdminDto(30L, "Bea", "bea@laesquina.test", true, false), creado);
        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).guardarConEmail(guardado.capture());
        Usuario u = guardado.getValue();
        assertEquals(LOCAL, u.getLocalId());
        assertEquals("ADMIN", u.getRol().getCodigo());
        assertEquals("hash(Provisoria1)", u.getHashPassword());
        assertTrue(u.isDebeCambiarCredencial());
        assertFalse(u.isContrasenaProvisoria());

        ArgumentCaptor<Empleado> empleado = ArgumentCaptor.forClass(Empleado.class);
        verify(empleados).guardar(empleado.capture());
        assertEquals(LOCAL, empleado.getValue().getLocalId());
        assertEquals(u, empleado.getValue().getUsuario());
    }

    @Test
    void elAltaQuedaEnLaAuditoriaSinLaContrasena() {
        servicio.crear(GERENTE, new DatosDeAltaDeAdmin("Bea", "bea@laesquina.test", "Provisoria1"));

        ArgumentCaptor<Object> nuevo = ArgumentCaptor.forClass(Object.class);
        verify(auditor).registrar(eq(LOCAL), eq(1L), eq("CREAR_ADMIN"), eq("USUARIO"), eq(30L), isNull(),
                nuevo.capture());
        assertEquals(Map.of("nombre", "Bea", "email", "bea@laesquina.test", "activo", true), nuevo.getValue());
    }

    @Test
    void puedeHaberVariosAdmins() {
        // CP-31-02: que ya haya uno no frena el alta.
        when(usuarios.listarAdminsDelLocal(LOCAL)).thenReturn(List.of(ana));

        servicio.crear(GERENTE, new DatosDeAltaDeAdmin("Bea", "bea@laesquina.test", "Provisoria1"));

        verify(usuarios).guardarConEmail(any(Usuario.class));
    }

    @Test
    void elEmailRepetidoDa409SinGuardarNada() {
        // CP-31-03: la consulta no distingue mayúsculas; acá alcanza con que el puerto diga que existe.
        when(usuarios.existeEmail("ADMIN@elpuerto.test")).thenReturn(true);

        ReglaDeNegocioException e = assertThrows(ReglaDeNegocioException.class,
                () -> servicio.crear(GERENTE, new DatosDeAltaDeAdmin("Bea", "ADMIN@elpuerto.test", "Provisoria1")));

        assertEquals("Ya hay un usuario con ese email", e.getMessage());
        verify(usuarios, never()).guardarConEmail(any());
        verifyNoInteractions(empleados, auditor);
    }

    @Test
    void loQueFaltaDa400ConElCampo() {
        assertEquals("Falta el nombre", faltante(new DatosDeAltaDeAdmin(" ", "bea@x.test", "Provisoria1")));
        assertEquals("Falta el email", faltante(new DatosDeAltaDeAdmin("Bea", null, "Provisoria1")));
        assertEquals("Falta la contraseña", faltante(new DatosDeAltaDeAdmin("Bea", "bea@x.test", null)));
        verifyNoInteractions(auditor);
    }

    private String faltante(DatosDeAltaDeAdmin datos) {
        return assertThrows(SolicitudInvalidaException.class, () -> servicio.crear(GERENTE, datos)).getMessage();
    }

    // --- La baja y la reactivación

    @Test
    void laBajaAuditaYRepetirlaNo() {
        // CP-31-04
        AdminDto dto = servicio.cambiarActivo(GERENTE, 20L, false);

        assertFalse(dto.activo());
        verify(auditor).registrar(LOCAL, 1L, "BAJA_ADMIN", "USUARIO", 20L, Map.of("activo", true),
                Map.of("activo", false));

        servicio.cambiarActivo(GERENTE, 20L, false);
        verify(usuarios).guardar(ana);
        verify(auditor).registrar(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void laReactivacionAudita() {
        ana.darDeBaja();

        AdminDto dto = servicio.cambiarActivo(GERENTE, 20L, true);

        assertTrue(dto.activo());
        verify(auditor).registrar(LOCAL, 1L, "REACTIVAR_ADMIN", "USUARIO", 20L, Map.of("activo", false),
                Map.of("activo", true));
    }

    @Test
    void sinActivoDa400() {
        SolicitudInvalidaException e = assertThrows(SolicitudInvalidaException.class,
                () -> servicio.cambiarActivo(GERENTE, 20L, null));
        assertEquals("Falta activo", e.getMessage());
    }

    // --- El desbloqueo

    @Test
    void elDesbloqueoVuelveLosIntentosACeroYAudita() {
        for (int i = 0; i < 5; i++) {
            ana.registrarIntentoFallido(5);
        }

        AdminDto dto = servicio.desbloquear(GERENTE, 20L);

        assertFalse(dto.bloqueado());
        assertEquals(0, ana.getIntentosFallidos());
        verify(auditor).registrar(LOCAL, 1L, "DESBLOQUEAR_ADMIN", "USUARIO", 20L,
                Map.of("intentosFallidos", 5, "bloqueado", true), Map.of("intentosFallidos", 0, "bloqueado", false));
    }

    @Test
    void desbloquearAUnoQueNoEstaBloqueadoNoCambiaNiAudita() {
        ana.registrarIntentoFallido(5);

        servicio.desbloquear(GERENTE, 20L);

        assertEquals(1, ana.getIntentosFallidos());
        verify(usuarios, never()).guardar(any());
        verifyNoInteractions(auditor);
    }

    @Test
    void desbloquearAUnDadoDeBajaDa409() {
        ana.darDeBaja();

        ReglaDeNegocioException e = assertThrows(ReglaDeNegocioException.class,
                () -> servicio.desbloquear(GERENTE, 20L));
        assertEquals("El Admin está dado de baja", e.getMessage());
        verifyNoInteractions(auditor);
    }

    // --- El 404

    @Test
    void unIdQueNoEsUnAdminDeSuLocalDa404() {
        assertEquals("El Admin no existe", assertThrows(NoEncontradoException.class,
                () -> servicio.cambiarActivo(GERENTE, 99L, false)).getMessage());
        assertEquals("El Admin no existe", assertThrows(NoEncontradoException.class,
                () -> servicio.desbloquear(GERENTE, 99L)).getMessage());
        verify(usuarios, times(2)).buscarAdminParaModificar(99L, LOCAL);
        verifyNoInteractions(auditor);
    }
}
