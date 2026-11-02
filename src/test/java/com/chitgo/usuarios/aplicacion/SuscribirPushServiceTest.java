package com.chitgo.usuarios.aplicacion;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import com.chitgo.compartido.dominio.ReglaDeNegocioException;
import com.chitgo.compartido.dominio.SolicitudInvalidaException;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;
import com.chitgo.usuarios.dominio.puertos.*;

class SuscribirPushServiceTest {
    private static final Instant AHORA = Instant.parse("2026-11-01T20:00:00Z");
    private static final UsuarioAutenticado JUAN = new UsuarioAutenticado(30L, "MOZO", 1L, false, AHORA.plusSeconds(600));
    private final SuscripcionesPush suscripciones = mock(SuscripcionesPush.class);
    private final PasarelaPush pasarela = mock(PasarelaPush.class);
    private final SuscribirPushService servicio = new SuscribirPushService(suscripciones, pasarela, () -> AHORA);

    @Test
    void registraElUsuarioDeLaSesionYUnHashDelEndpoint() {
        when(pasarela.habilitada()).thenReturn(true);
        servicio.registrar(JUAN, "https://fcm.googleapis.com/token", "clave", "auth");
        verify(suscripciones).registrar(argThat(s -> s.getUsuarioId().equals(30L) && s.getLocalId().equals(1L)
                && s.getEndpointHash().matches("[a-f0-9]{64}") && s.getActualizadaEn().equals(AHORA)));
        verify(pasarela).validar("https://fcm.googleapis.com/token", "clave", "auth");
    }

    @Test
    void noGuardaSuscripcionesSiPushEstaApagado() {
        assertThatThrownBy(() -> servicio.registrar(JUAN, "endpoint", "clave", "auth"))
                .isInstanceOf(ReglaDeNegocioException.class);
        verifyNoInteractions(suscripciones);
    }

    @Test
    void unaSuscripcionInvalidaNoSeGuarda() {
        when(pasarela.habilitada()).thenReturn(true);
        doThrow(new SolicitudInvalidaException("inválida")).when(pasarela).validar(any(), any(), any());
        assertThatThrownBy(() -> servicio.registrar(JUAN, "endpoint", "clave", "auth"))
                .isInstanceOf(SolicitudInvalidaException.class);
        verifyNoInteractions(suscripciones);
    }

    @Test
    void soloPuedeEliminarSuSuscripcionDelLocal() {
        servicio.eliminar(JUAN, "endpoint");
        verify(suscripciones).eliminar(matches("[a-f0-9]{64}"), eq(30L), eq(1L));
    }
}
