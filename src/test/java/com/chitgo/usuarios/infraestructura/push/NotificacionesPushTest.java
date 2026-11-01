package com.chitgo.usuarios.infraestructura.push;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import com.chitgo.pedidos.dominio.*;
import com.chitgo.pedidos.infraestructura.websocket.AvisoParaPush;
import com.chitgo.usuarios.aplicacion.DestinatariosPushService;
import com.chitgo.usuarios.dominio.SuscripcionPush;
import com.chitgo.usuarios.dominio.puertos.*;

class NotificacionesPushTest {
    private final DestinatariosPushService destinatarios = mock(DestinatariosPushService.class);
    private final SuscripcionesPush suscripciones = mock(SuscripcionesPush.class);
    private final PasarelaPush pasarela = mock(PasarelaPush.class);
    private final NotificacionesPush notificador = new NotificacionesPush(destinatarios, suscripciones, pasarela, new ObjectMapper());
    private final EventoTiempoReal evento = EventoTiempoReal.de(TipoEvento.PEDIDO_LISTO, 5L, 1L, "LISTO", null, Instant.EPOCH);
    private final SuscripcionPush celular = new SuscripcionPush(1L, 30L, "endpoint", "hash", "clave", "auth", Instant.EPOCH);

    @Test
    void losEndpointsVencidosSeEliminanSinAfectarLosOtrosDispositivos() throws Exception {
        when(pasarela.habilitada()).thenReturn(true);
        SuscripcionPush otro = new SuscripcionPush(1L, 30L, "otro", "hash2", "clave", "auth", Instant.EPOCH);
        when(destinatarios.para(1L, evento)).thenReturn(List.of(celular, otro));
        when(pasarela.enviar(eq(celular), anyString())).thenReturn(410);
        when(pasarela.enviar(eq(otro), anyString())).thenReturn(201);
        notificador.enviar(new AvisoParaPush(1L, evento));
        verify(suscripciones).eliminar("hash", 30L, 1L);
        verify(pasarela).enviar(eq(otro), contains("PEDIDO_LISTO"));
        verify(suscripciones, never()).eliminar(eq("hash2"), any(), any());
    }

    @Test
    void unFalloDeRedNoBorraLaSuscripcionNiImpideElEnvioAlOtroDispositivo() throws Exception {
        when(pasarela.habilitada()).thenReturn(true);
        SuscripcionPush otro = new SuscripcionPush(1L, 30L, "otro", "hash2", "clave", "auth", Instant.EPOCH);
        when(destinatarios.para(1L, evento)).thenReturn(List.of(celular, otro));
        when(pasarela.enviar(eq(celular), anyString())).thenThrow(new java.io.IOException());
        when(pasarela.enviar(eq(otro), anyString())).thenReturn(201);
        notificador.enviar(new AvisoParaPush(1L, evento));
        verify(pasarela).enviar(eq(otro), anyString());
        verifyNoInteractions(suscripciones);
    }

    @Test
    void pushApagadoNoConsultaLaBaseNiEnvia() {
        notificador.enviar(new AvisoParaPush(1L, evento));
        verifyNoInteractions(destinatarios, suscripciones);
    }
}
