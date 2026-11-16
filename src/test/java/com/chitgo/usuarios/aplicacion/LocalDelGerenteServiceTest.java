package com.chitgo.usuarios.aplicacion;

import static com.chitgo.pruebas.Entidades.conId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.usuarios.dominio.Local;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;
import com.chitgo.usuarios.dominio.puertos.LocalRepository;

/**
 * HU-16, tarea 3 de la ST-24, con mocks. El Gerente/Dueño (usuario 1) es propietario de La Esquina (local 7); el
 * usuario 2 es Gerente/Dueño sin local.
 */
class LocalDelGerenteServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-16T15:00:00Z");
    private static final Long LOCAL = 7L;
    private static final UsuarioAutenticado GERENTE = new UsuarioAutenticado(1L, "GERENTE_DUENO", null, false,
            AHORA.plusSeconds(600));
    private static final UsuarioAutenticado SIN_LOCAL = new UsuarioAutenticado(2L, "GERENTE_DUENO", null, false,
            AHORA.plusSeconds(600));

    private final LocalRepository locales = mock(LocalRepository.class);
    private final LocalDelGerenteService servicio = new LocalDelGerenteService(locales);

    private void conLocalPropio() {
        when(locales.buscarPorPropietario(1L)).thenReturn(Optional.of(conId(new Local(1L, "La Esquina", null, "t"), LOCAL)));
        when(locales.buscarPorPropietario(2L)).thenReturn(Optional.empty());
    }

    @Test
    void sinLocalIdDaElLocalDelPropietario() {
        conLocalPropio();
        assertEquals(LOCAL, servicio.resolver(GERENTE, null));
    }

    @Test
    void conSuPropioLocalIdDaElMismo() {
        conLocalPropio();
        assertEquals(LOCAL, servicio.resolver(GERENTE, LOCAL));
    }

    @Test
    void unLocalIdAjenoOInexistenteDa404ConElMismoTexto() {
        conLocalPropio();
        NoEncontradoException ajeno = assertThrows(NoEncontradoException.class, () -> servicio.resolver(GERENTE, 8L));
        NoEncontradoException inexistente = assertThrows(NoEncontradoException.class,
                () -> servicio.resolver(GERENTE, 999999L));
        assertEquals("El local no existe", ajeno.getMessage());
        assertEquals(ajeno.getMessage(), inexistente.getMessage());
    }

    @Test
    void elGerenteSinLocalDa404ConOSinLocalId() {
        conLocalPropio();
        assertEquals("El local no existe",
                assertThrows(NoEncontradoException.class, () -> servicio.resolver(SIN_LOCAL, null)).getMessage());
        assertThrows(NoEncontradoException.class, () -> servicio.resolver(SIN_LOCAL, LOCAL));
    }

    @Test
    void otroRolDa403SinMirarLaBaseAunqueMandeSuLocal() {
        for (String rol : new String[] {"ADMIN", "CAJA", "MOZO", "COCINA"}) {
            UsuarioAutenticado otro = new UsuarioAutenticado(3L, rol, LOCAL, false, AHORA.plusSeconds(600));
            assertThrows(AccesoDenegadoException.class, () -> servicio.resolver(otro, null));
            assertThrows(AccesoDenegadoException.class, () -> servicio.resolver(otro, LOCAL));
        }
        verifyNoInteractions(locales);
    }

    @Test
    void elLocalDelPedidoNuncaEsLoQueSeBuscaPorPropietario() {
        conLocalPropio();
        assertThrows(NoEncontradoException.class, () -> servicio.resolver(GERENTE, 8L));
        org.mockito.Mockito.verify(locales, org.mockito.Mockito.never()).buscarPorId(anyLong());
    }
}
