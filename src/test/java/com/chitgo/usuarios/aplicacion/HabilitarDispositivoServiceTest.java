package com.chitgo.usuarios.aplicacion;

import static com.chitgo.pruebas.Entidades.conId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chitgo.compartido.dominio.RelojDePrueba;
import com.chitgo.usuarios.dominio.Local;
import com.chitgo.usuarios.dominio.Rol;
import com.chitgo.usuarios.dominio.Usuario;
import com.chitgo.usuarios.dominio.UsuarioBloqueadoException;
import com.chitgo.usuarios.dominio.UsuarioOContrasenaIncorrectosException;
import com.chitgo.usuarios.dominio.puertos.CodificadorDeClaves;
import com.chitgo.usuarios.dominio.puertos.EmisorDeHabilitaciones;
import com.chitgo.usuarios.dominio.puertos.LocalRepository;
import com.chitgo.usuarios.dominio.puertos.UsuarioRepository;

/**
 * El mozo bloqueado en POST /auth/mozo/login (CP-10-12), con mocks: 423 solo con la contraseña correcta (B',
 * decidido el 20/10, punto 4). Que tarde lo mismo (CP-11-04) se mira por lo que hace: en los tres casos se verifica
 * una contraseña contra un hash, que es lo que cuesta. Medir milisegundos en un test daría resultados inestables.
 */
class HabilitarDispositivoServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-16T15:00:00Z");
    private static final String TOKEN = "token-la-esquina";
    private static final String CORRECTA = "Chitgo2026!";
    private static final String INCORRECTA = "otraContrasena";

    private final LocalRepository locales = mock(LocalRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final CodificadorDeClaves codificador = mock(CodificadorDeClaves.class);
    private final EmisorDeHabilitaciones emisor = mock(EmisorDeHabilitaciones.class);
    private final RelojDePrueba reloj = new RelojDePrueba(AHORA);

    private Usuario juan;
    private HabilitarDispositivoService servicio;

    @BeforeEach
    void preparar() {
        Local laEsquina = conId(new Local(1L, "La Esquina", "Calle 1", TOKEN), 1L);
        juan = conId(new Usuario(1L, new Rol("MOZO", "Mozo"), "Juan", "juan", null, "hashPass", "hashPin"), 10L);
        juan.elegirCredencial(null, null);
        for (int i = 0; i < Usuario.INTENTOS_PARA_BLOQUEAR; i++) {
            juan.registrarIntentoFallido();
        }
        when(locales.buscarPorTokenStaff(TOKEN)).thenReturn(Optional.of(laEsquina));
        when(usuarios.buscarPorNombreDeUsuarioParaIngreso(1L, "juan")).thenReturn(Optional.of(juan));
        when(codificador.hashear(anyString())).thenReturn("relleno");
        when(codificador.verificar(CORRECTA, "hashPass")).thenReturn(true);
        servicio = new HabilitarDispositivoService(locales, usuarios, codificador, emisor, reloj);
    }

    @Test
    void bloqueado_con_la_contrasena_correcta_da_423_y_no_habilita_el_dispositivo() {
        UsuarioBloqueadoException e = assertThrows(UsuarioBloqueadoException.class,
                () -> servicio.habilitar(TOKEN, "juan", CORRECTA));

        assertEquals("Tu usuario está bloqueado. Pedile al Admin que te blanquee el PIN.", e.getMessage());
        verify(emisor, never()).emitir(any());
        // Sigue bloqueado: el 423 no es un ingreso.
        assertEquals(Usuario.INTENTOS_PARA_BLOQUEAR, juan.getIntentosFallidos());
        assertEquals(Usuario.BLOQUEO_SIN_VENCIMIENTO, juan.getBloqueadoHasta());
    }

    @Test
    void bloqueado_con_la_contrasena_incorrecta_da_el_401_generico_sin_sumar_intentos() {
        assertThrows(UsuarioOContrasenaIncorrectosException.class,
                () -> servicio.habilitar(TOKEN, "juan", INCORRECTA));

        verify(emisor, never()).emitir(any());
        assertEquals(Usuario.INTENTOS_PARA_BLOQUEAR, juan.getIntentosFallidos());
    }

    @Test
    void los_tres_casos_verifican_una_sola_contrasena_contra_un_hash() {
        // Bloqueado con la correcta, bloqueado con la incorrecta, y un usuario que no existe (contra el relleno).
        assertThrows(UsuarioBloqueadoException.class, () -> servicio.habilitar(TOKEN, "juan", CORRECTA));
        assertThrows(UsuarioOContrasenaIncorrectosException.class,
                () -> servicio.habilitar(TOKEN, "juan", INCORRECTA));
        assertThrows(UsuarioOContrasenaIncorrectosException.class,
                () -> servicio.habilitar(TOKEN, "nadie", CORRECTA));

        verify(codificador).verificar(CORRECTA, "hashPass");
        verify(codificador).verificar(INCORRECTA, "hashPass");
        verify(codificador).verificar(CORRECTA, "relleno");
        verify(codificador, times(3)).verificar(anyString(), anyString());
    }
}
