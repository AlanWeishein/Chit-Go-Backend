package com.chitgo.usuarios.infraestructura.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;

import com.chitgo.compartido.dominio.ReglaDeNegocioException;
import com.chitgo.usuarios.aplicacion.AltaDeLocalService;
import com.chitgo.usuarios.aplicacion.AltaDeLocalService.ResultadoDelDesbloqueo;
import com.chitgo.usuarios.aplicacion.dto.DatosDeAltaDeLocal;
import com.chitgo.usuarios.aplicacion.dto.LocalCreado;

/** La cáscara del script, sin Spring: los argumentos, la contraseña, lo que muestra y con qué código sale. */
class ComandosDelLocalTest {

    private static final String TOKEN = "11111111-2222-4333-8444-555555555555";

    private final AltaDeLocalService servicio = mock(AltaDeLocalService.class);
    private final ByteArrayOutputStream salida = new ByteArrayOutputStream();
    private String contrasena = "Provisoria2026!";

    private final ComandosDelLocal comandos = new ComandosDelLocal(servicio, null, "http://localhost:5173/",
            () -> contrasena, new PrintStream(salida, true, StandardCharsets.UTF_8));

    @Test
    void elAltaPasaLosDatosYMuestraElTokenYLaUrlDelLogin() {
        when(servicio.darDeAlta(any())).thenReturn(new LocalCreado(7L, "La Prueba", TOKEN, 40L, "d@p.test"));

        int codigo = ejecutar("--spring.profiles.active=script", "alta-local", "--local=La Prueba",
                "--direccion=Calle 1", "--gerente=Dueño", "--email=d@p.test");

        assertThat(codigo).isEqualTo(ComandosDelLocal.BIEN);
        ArgumentCaptor<DatosDeAltaDeLocal> datos = ArgumentCaptor.forClass(DatosDeAltaDeLocal.class);
        verify(servicio).darDeAlta(datos.capture());
        assertThat(datos.getValue())
                .isEqualTo(new DatosDeAltaDeLocal("La Prueba", "Calle 1", "Dueño", "d@p.test", "Provisoria2026!"));
        assertThat(texto()).contains("id 7", "Token de staff: " + TOKEN,
                "Login del personal: http://localhost:5173/staff/" + TOKEN).doesNotContain("Provisoria2026!");
    }

    @Test
    void laContrasenaComoArgumentoSeRechazaSinLlamarAlService() {
        assertThat(ejecutar("alta-local", "--local=X", "--password=Provisoria2026!"))
                .isEqualTo(ComandosDelLocal.MAL_ESCRITO);
        assertThat(ejecutar("alta-local", "--local=X", "--contrasena=Provisoria2026!"))
                .isEqualTo(ComandosDelLocal.MAL_ESCRITO);
        verifyNoInteractions(servicio);
        assertThat(texto()).contains("no va como argumento");
    }

    @Test
    void sinContrasenaNoLlamaAlService() {
        contrasena = null;
        assertThat(ejecutar("alta-local", "--local=X")).isEqualTo(ComandosDelLocal.MAL_ESCRITO);
        verifyNoInteractions(servicio);
        assertThat(texto()).contains(ComandosDelLocal.VARIABLE);
    }

    @Test
    void sinComandoOConUnoQueNoExisteMuestraLaAyuda() {
        assertThat(ejecutar("--spring.profiles.active=script")).isEqualTo(ComandosDelLocal.MAL_ESCRITO);
        assertThat(ejecutar("borrar-local")).isEqualTo(ComandosDelLocal.MAL_ESCRITO);
        assertThat(ejecutar("alta-local", "desbloquear-gerente")).isEqualTo(ComandosDelLocal.MAL_ESCRITO);
        verifyNoInteractions(servicio);
        assertThat(texto()).contains("No existe el comando borrar-local", "desbloquear-gerente --email=");
    }

    @Test
    void unErrorDelServiceSaleConUnoYLoDice() {
        when(servicio.darDeAlta(any())).thenThrow(new ReglaDeNegocioException("Ya hay un usuario con ese email"));

        assertThat(ejecutar("alta-local", "--local=X", "--gerente=Y", "--email=dueno@laesquina.test"))
                .isEqualTo(ComandosDelLocal.NO_CAMBIO_NADA);
        assertThat(texto()).contains("No se guardó nada: Ya hay un usuario con ese email.");
    }

    @Test
    void elDesbloqueoDiceQuePaso() {
        when(servicio.desbloquearGerente("d@p.test")).thenReturn(ResultadoDelDesbloqueo.DESBLOQUEADO);
        when(servicio.desbloquearGerente("libre@p.test")).thenReturn(ResultadoDelDesbloqueo.NO_ESTABA_BLOQUEADO);
        when(servicio.desbloquearGerente("admin@p.test")).thenReturn(ResultadoDelDesbloqueo.NO_ES_GERENTE);
        when(servicio.desbloquearGerente("solo@p.test")).thenReturn(ResultadoDelDesbloqueo.SIN_LOCAL);

        assertThat(ejecutar("desbloquear-gerente", "--email=d@p.test")).isEqualTo(ComandosDelLocal.BIEN);
        assertThat(ejecutar("desbloquear-gerente", "--email=libre@p.test")).isEqualTo(ComandosDelLocal.BIEN);
        assertThat(ejecutar("desbloquear-gerente", "--email=admin@p.test")).isEqualTo(ComandosDelLocal.NO_CAMBIO_NADA);
        assertThat(ejecutar("desbloquear-gerente", "--email=solo@p.test")).isEqualTo(ComandosDelLocal.NO_CAMBIO_NADA);
        assertThat(texto()).contains("desbloqueado: d@p.test", "no estaba bloqueado",
                "No hay un Gerente/Dueño con el email admin@p.test", "no tiene un local");
    }

    private int ejecutar(String... argumentos) {
        return comandos.ejecutar(new DefaultApplicationArguments(argumentos));
    }

    private String texto() {
        return salida.toString(StandardCharsets.UTF_8);
    }
}
