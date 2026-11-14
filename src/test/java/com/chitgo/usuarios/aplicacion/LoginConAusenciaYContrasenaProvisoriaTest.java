package com.chitgo.usuarios.aplicacion;

import static com.chitgo.pruebas.Entidades.conId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.compartido.dominio.RelojDePrueba;
import com.chitgo.usuarios.aplicacion.dto.SesionDto;
import com.chitgo.usuarios.aplicacion.dto.StaffDto;
import com.chitgo.usuarios.dominio.CredencialNuevaInvalidaException;
import com.chitgo.usuarios.dominio.DatosDeSesion;
import com.chitgo.usuarios.dominio.Local;
import com.chitgo.usuarios.dominio.Rol;
import com.chitgo.usuarios.dominio.TokenLeido;
import com.chitgo.usuarios.dominio.Usuario;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;
import com.chitgo.usuarios.dominio.UsuarioOContrasenaIncorrectosException;
import com.chitgo.usuarios.dominio.puertos.CodificadorDeClaves;
import com.chitgo.usuarios.dominio.puertos.EmisorDeHabilitaciones;
import com.chitgo.usuarios.dominio.puertos.EmisorDeTokens;
import com.chitgo.usuarios.dominio.puertos.LocalRepository;
import com.chitgo.usuarios.dominio.puertos.UsuarioRepository;

/**
 * HU-14, tarea 3 de la ST-22, en los Services del login y con mocks: la ausencia vigente (decidido el 20/10, punto
 * 11) y la contraseña provisoria (punto 10). Son las 23 en Buenos Aires y ya las 2 del día siguiente en UTC: "hoy"
 * tiene que ser el día del local.
 */
class LoginConAusenciaYContrasenaProvisoriaTest {

    private static final Instant AHORA = Instant.parse("2026-11-15T02:00:00Z");
    private static final LocalDate HOY_EN_EL_LOCAL = LocalDate.of(2026, 11, 14);
    private static final String TOKEN = "token-la-esquina";

    private final LocalRepository locales = mock(LocalRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final CodificadorDeClaves codificador = mock(CodificadorDeClaves.class);
    private final RelojDePrueba reloj = new RelojDePrueba(AHORA);

    private Local laEsquina;
    private Usuario carla;
    private Usuario juan;

    @BeforeEach
    void preparar() {
        laEsquina = conId(new Local(1L, "La Esquina", "Calle 1", TOKEN), 1L);
        carla = conId(new Usuario(1L, new Rol("CAJA", "Caja"), "Carla", null, null, null, "hashPin"), 11L);
        carla.elegirCredencial(null, null);
        juan = conId(new Usuario(1L, new Rol("MOZO", "Mozo"), "Juan", "juan", null, "hashPass", "hashPin"), 10L);
        juan.elegirCredencial(null, null);
        when(locales.buscarPorTokenStaff(TOKEN)).thenReturn(Optional.of(laEsquina));
        when(locales.buscarPorId(1L)).thenReturn(Optional.of(laEsquina));
        when(usuarios.buscarPorIdParaIngreso(11L)).thenReturn(Optional.of(carla));
        when(usuarios.buscarPorIdParaIngreso(10L)).thenReturn(Optional.of(juan));
        when(usuarios.buscarPorNombreDeUsuarioParaIngreso(1L, "juan")).thenReturn(Optional.of(juan));
        when(codificador.hashear(anyString())).thenAnswer(inv -> "hash(" + inv.getArgument(0) + ")");
    }

    // --- La lista del login

    @Test
    void la_lista_pide_a_los_que_no_tienen_ausencia_con_el_dia_del_local() {
        when(usuarios.listarParaElLogin(eq(1L), any(), eq(HOY_EN_EL_LOCAL))).thenReturn(List.of(carla));

        StaffDto staff = new ConsultarStaffService(locales, usuarios, reloj).consultar(TOKEN);

        assertEquals(List.of("Carla"), staff.usuarios().stream().map(StaffDto.UsuarioDeStaffDto::nombre).toList());
        verify(usuarios).listarParaElLogin(1L, Rol.CODIGOS_CON_PIN, HOY_EN_EL_LOCAL);
    }

    // --- POST /auth/pin

    @Test
    void con_una_ausencia_vigente_el_pin_da_el_404_de_la_lista_y_no_suma_intentos() {
        when(usuarios.tieneAusenciaVigente(11L, 1L, HOY_EN_EL_LOCAL)).thenReturn(true);
        AperturaDeSesion apertura = mock(AperturaDeSesion.class);

        NoEncontradoException e = assertThrows(NoEncontradoException.class, () -> ingresarConPin(apertura)
                .ingresar(TOKEN, 11L, "2468", null));

        assertEquals("El usuario no está en la lista de este local.", e.getMessage());
        assertEquals(0, carla.getIntentosFallidos());
        verify(codificador, never()).verificar(anyString(), anyString());
        verify(apertura, never()).abrir(any(), any());
    }

    @Test
    void con_una_ausencia_vigente_el_bloqueado_tambien_ve_el_404_y_no_el_423() {
        for (int i = 0; i < Usuario.INTENTOS_PARA_BLOQUEAR; i++) {
            carla.registrarIntentoFallido();
        }
        when(usuarios.tieneAusenciaVigente(11L, 1L, HOY_EN_EL_LOCAL)).thenReturn(true);

        assertThrows(NoEncontradoException.class, () -> ingresarConPin(mock(AperturaDeSesion.class))
                .ingresar(TOKEN, 11L, "2468", null));
    }

    @Test
    void sin_ausencia_vigente_el_pin_entra_como_siempre() {
        when(codificador.verificar("2468", "hashPin")).thenReturn(true);
        AperturaDeSesion apertura = mock(AperturaDeSesion.class);

        ingresarConPin(apertura).ingresar(TOKEN, 11L, "2468", null);

        verify(usuarios).tieneAusenciaVigente(11L, 1L, HOY_EN_EL_LOCAL);
        verify(apertura).abrir(carla, laEsquina);
    }

    // --- POST /auth/mozo/login

    @Test
    void con_una_ausencia_vigente_el_mozo_recibe_el_401_generico_sin_sumar_intentos() {
        when(usuarios.tieneAusenciaVigente(10L, 1L, HOY_EN_EL_LOCAL)).thenReturn(true);
        when(codificador.hashear(anyString())).thenReturn("relleno");
        EmisorDeHabilitaciones emisor = mock(EmisorDeHabilitaciones.class);
        HabilitarDispositivoService servicio = new HabilitarDispositivoService(locales, usuarios, codificador, emisor,
                reloj);

        assertThrows(UsuarioOContrasenaIncorrectosException.class,
                () -> servicio.habilitar(TOKEN, "juan", "Chitgo2026!"));

        // Como un usuario que no existe: verifica contra el relleno, para que tarde lo mismo.
        verify(codificador).verificar("Chitgo2026!", "relleno");
        verify(codificador, never()).verificar("Chitgo2026!", "hashPass");
        assertEquals(0, juan.getIntentosFallidos());
        verify(emisor, never()).emitir(any());
    }

    // --- POST /auth/credencial

    @Test
    void con_la_contrasena_provisoria_el_mozo_tiene_que_elegirla() {
        Usuario pedro = mozoNuevo();

        CredencialNuevaInvalidaException e = assertThrows(CredencialNuevaInvalidaException.class,
                () -> elegirCredencial(mock(AperturaDeSesion.class)).elegir(20L, "4321", "4321", null, null));

        assertEquals("Elegí tu contraseña.", e.getMessage());
        assertEquals("hashPin", pedro.getPinHash());
        assertTrue(pedro.isDebeCambiarCredencial());
        assertTrue(pedro.isContrasenaProvisoria());
    }

    @Test
    void sin_nada_el_mozo_con_contrasena_provisoria_ve_primero_que_falta_el_pin() {
        mozoNuevo();

        CredencialNuevaInvalidaException e = assertThrows(CredencialNuevaInvalidaException.class,
                () -> elegirCredencial(mock(AperturaDeSesion.class)).elegir(20L, null, null, null, null));

        assertEquals("Elegí tu PIN.", e.getMessage());
    }

    @Test
    void al_elegir_la_contrasena_deja_de_ser_provisoria() {
        Usuario pedro = mozoNuevo();

        elegirCredencial(mock(AperturaDeSesion.class)).elegir(20L, "4321", "4321", "MiClaveNueva1", "MiClaveNueva1");

        assertFalse(pedro.isContrasenaProvisoria());
        assertFalse(pedro.isDebeCambiarCredencial());
        assertEquals("hash(MiClaveNueva1)", pedro.getHashPassword());
    }

    @Test
    void despues_de_un_blanqueo_alcanza_con_el_pin() {
        juan.blanquearPin("hashProvisorio");

        elegirCredencial(mock(AperturaDeSesion.class)).elegir(10L, "4321", "4321", null, null);

        assertFalse(juan.isDebeCambiarCredencial());
        assertEquals("hash(4321)", juan.getPinHash());
        assertEquals("hashPass", juan.getHashPassword());
    }

    // --- contrasenaProvisoria en la sesión

    @Test
    void la_sesion_de_un_login_lleva_la_contrasena_provisoria_del_usuario() {
        Usuario pedro = mozoNuevo();
        EmisorDeTokens emisor = mock(EmisorDeTokens.class);
        when(emisor.emitir(any(DatosDeSesion.class), any(Duration.class))).thenReturn("jwt");
        when(emisor.leer("jwt")).thenReturn(new TokenLeido(null, AHORA.plusSeconds(600)));

        SesionDto sesion = new AperturaDeSesion(emisor).abrir(pedro, laEsquina).sesion();

        assertTrue(sesion.debeCambiarCredencial());
        assertTrue(sesion.contrasenaProvisoria());
        assertFalse(new AperturaDeSesion(emisor).abrir(juan, laEsquina).sesion().contrasenaProvisoria());
    }

    @Test
    void auth_sesion_lee_la_contrasena_provisoria_de_la_base_y_no_del_token() {
        // El token todavía dice que debe cambiarla, pero desde otra pestaña ya eligió la contraseña.
        Usuario pedro = mozoNuevo();
        pedro.elegirCredencial("pinNuevo", "passNueva");
        when(usuarios.buscarPorId(20L)).thenReturn(Optional.of(pedro));
        UsuarioAutenticado token = new UsuarioAutenticado(20L, "MOZO", 1L, true, AHORA.plusSeconds(600));

        SesionDto sesion = new ConsultarSesionService(usuarios, locales).consultar(token);

        assertTrue(sesion.debeCambiarCredencial());
        assertFalse(sesion.contrasenaProvisoria());
    }

    // --- Ayudas

    /** Pedro (usuario 20), recién dado de alta: PIN y contraseña provisorios. */
    private Usuario mozoNuevo() {
        Usuario pedro = conId(Usuario.nuevoEmpleado(1L, new Rol("MOZO", "Mozo"), "Pedro", "pedro", "hashPass",
                "hashPin"), 20L);
        when(usuarios.buscarPorIdParaIngreso(20L)).thenReturn(Optional.of(pedro));
        return pedro;
    }

    private IngresarConPinService ingresarConPin(AperturaDeSesion apertura) {
        return new IngresarConPinService(locales, usuarios, codificador, mock(EmisorDeHabilitaciones.class), apertura,
                reloj);
    }

    private ElegirCredencialService elegirCredencial(AperturaDeSesion apertura) {
        return new ElegirCredencialService(usuarios, locales, codificador, apertura);
    }
}
