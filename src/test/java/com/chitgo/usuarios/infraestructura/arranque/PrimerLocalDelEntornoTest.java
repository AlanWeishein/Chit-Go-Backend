package com.chitgo.usuarios.infraestructura.arranque;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import com.chitgo.compartido.dominio.ReglaDeNegocioException;
import com.chitgo.compartido.dominio.SolicitudInvalidaException;
import com.chitgo.usuarios.aplicacion.AltaDeLocalService;
import com.chitgo.usuarios.aplicacion.dto.DatosDeAltaDeLocal;
import com.chitgo.usuarios.aplicacion.dto.LocalCreado;
import com.chitgo.usuarios.dominio.puertos.LocalRepository;
import com.chitgo.usuarios.infraestructura.arranque.PrimerLocalDelEntorno.Variables;

/**
 * El primer local del .env, sin Spring: un test de integración no sirve para "la base sin locales", porque la base de
 * los tests tiene la semilla de dev. Lo que el log muestra, qué le pasa al alta y cuándo el back no arranca.
 */
class PrimerLocalDelEntornoTest {

    private static final String CONTRASENA = "Provisoria2026!";
    private static final String TOKEN = "11111111-2222-4333-8444-555555555555";
    private static final Variables LAS_CINCO =
            new Variables("La Prueba", "Calle 1", "Dueño La Prueba", "dueno@laprueba.test", CONTRASENA);

    private final AltaDeLocalService servicio = mock(AltaDeLocalService.class);
    private final LocalRepository locales = mock(LocalRepository.class);
    private final List<String> log = new ArrayList<>();

    @Test
    void sinNingunaVariableNoHaceNada() {
        primerLocal(new Variables("", null, "  ", "", null)).run(new DefaultApplicationArguments());

        verifyNoInteractions(servicio, locales);
        assertThat(log).isEmpty();
    }

    @Test
    void conLasCincoYLaBaseSinLocalesCreaElLocalYMuestraElLoginDelPersonal() {
        when(locales.hayLocales()).thenReturn(false);
        when(servicio.darDeAltaDesdeElEntorno(any()))
                .thenReturn(new LocalCreado(7L, "La Prueba", TOKEN, 40L, "dueno@laprueba.test"));

        primerLocal(LAS_CINCO, "http://localhost:5173/").run(new DefaultApplicationArguments());

        verify(servicio).darDeAltaDesdeElEntorno(
                new DatosDeAltaDeLocal("La Prueba", "Calle 1", "Dueño La Prueba", "dueno@laprueba.test", CONTRASENA));
        verify(servicio, never()).darDeAlta(any());
        assertThat(String.join("\n", log))
                .contains("La Prueba (id 7)", "dueno@laprueba.test",
                        "Login del personal: http://localhost:5173/staff/" + TOKEN, "provisoria")
                .doesNotContain(CONTRASENA);
    }

    @Test
    void conLasCincoYLaBaseConLocalesNoCreaNadaYLoDice() {
        when(locales.hayLocales()).thenReturn(true);

        primerLocal(LAS_CINCO).run(new DefaultApplicationArguments());

        verifyNoInteractions(servicio);
        assertThat(log).singleElement().asString().contains("ya tiene locales", "no se creó nada");
    }

    @Test
    void conAlgunasSiYOtrasNoNoArrancaYNombraLasQueFaltan() {
        Variables algunas = new Variables("La Prueba", "Calle 1", "Dueño La Prueba", "   ", null);

        assertThatThrownBy(() -> primerLocal(algunas).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CHITGO_GERENTE_EMAIL, CHITGO_GERENTE_CONTRASENA")
                .hasMessageNotContaining("CHITGO_LOCAL_NOMBRE");
        verifyNoInteractions(servicio, locales);
    }

    @Test
    void faltaUnaSolaYLaNombra() {
        Variables sinDireccion = new Variables("La Prueba", "", "Dueño La Prueba", "dueno@laprueba.test", CONTRASENA);

        assertThatThrownBy(() -> primerLocal(sinDireccion).run(new DefaultApplicationArguments()))
                .hasMessageContaining("Faltan variables del primer local: CHITGO_LOCAL_DIRECCION.")
                .hasMessageNotContaining(CONTRASENA);
        verifyNoInteractions(servicio, locales);
    }

    @Test
    void unDatoQueNoSirveNoDejaArrancarConElMensajeDelAlta() {
        when(locales.hayLocales()).thenReturn(false);
        when(servicio.darDeAltaDesdeElEntorno(any()))
                .thenThrow(new SolicitudInvalidaException("El email no es válido. La contraseña es demasiado corta"));

        assertThatThrownBy(() -> primerLocal(LAS_CINCO).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No se creó el primer local: El email no es válido. La contraseña es demasiado corta")
                .hasMessageNotContaining(CONTRASENA);
        assertThat(log).isEmpty();
    }

    @Test
    void elEmailRepetidoNoDejaArrancar() {
        when(locales.hayLocales()).thenReturn(false);
        when(servicio.darDeAltaDesdeElEntorno(any()))
                .thenThrow(new ReglaDeNegocioException("Ya hay un usuario con ese email"));

        assertThatThrownBy(() -> primerLocal(LAS_CINCO).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No se creó el primer local: Ya hay un usuario con ese email");
    }

    @Test
    void lasVariablesNoMuestranLaContrasena() {
        assertThat(LAS_CINCO.toString()).contains("La Prueba", "dueno@laprueba.test").doesNotContain(CONTRASENA);
    }

    private PrimerLocalDelEntorno primerLocal(Variables variables) {
        return primerLocal(variables, "http://localhost:5173");
    }

    private PrimerLocalDelEntorno primerLocal(Variables variables, String urlFront) {
        return new PrimerLocalDelEntorno(servicio, locales, variables, urlFront, log::add);
    }
}
