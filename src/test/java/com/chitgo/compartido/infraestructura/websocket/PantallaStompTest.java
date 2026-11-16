package com.chitgo.compartido.infraestructura.websocket;

import static com.chitgo.pruebas.Entidades.conId;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import com.chitgo.pedidos.dominio.puertos.ClienteSesionRepository;
import com.chitgo.usuarios.dominio.Local;
import com.chitgo.usuarios.dominio.puertos.LocalRepository;
import com.chitgo.usuarios.infraestructura.seguridad.AutenticadorPorToken;

class PantallaStompTest {
    private final LocalRepository locales = mock(LocalRepository.class);
    private final AutenticacionStompInterceptor interceptor = new AutenticacionStompInterceptor(
            mock(AutenticadorPorToken.class), mock(ClienteSesionRepository.class), locales);

    @Test
    void elTokenSoloPermiteElTopicDePantallaDeSuLocal() {
        when(locales.buscarPorTokenPantalla("pantalla")).thenReturn(Optional.of(conId(new Local(1L,"Local","","staff"), 7L)));
        var conectar = StompHeaderAccessor.create(StompCommand.CONNECT);
        conectar.setNativeHeader("tokenPantalla", "pantalla");
        conectar.setLeaveMutable(true);
        var mensaje = interceptor.preSend(MessageBuilder.createMessage(new byte[0], conectar.getMessageHeaders()), null);
        var principal = MessageHeaderAccessor.getAccessor(mensaje, StompHeaderAccessor.class).getUser();
        assertThat(principal).isEqualTo(new PrincipalDePantalla(7L));
        for (String destino : java.util.List.of("/topic/local/8/pantalla", "/topic/local/7/cocina/cola", "/user/queue/pedido")) {
            var suscribir = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
            suscribir.setUser(principal); suscribir.setDestination(destino); suscribir.setLeaveMutable(true);
            assertThatThrownBy(() -> interceptor.preSend(MessageBuilder.createMessage(new byte[0], suscribir.getMessageHeaders()), null))
                    .isInstanceOf(MessagingException.class);
        }
        var permitido = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        permitido.setUser(principal); permitido.setDestination("/topic/local/7/pantalla"); permitido.setLeaveMutable(true);
        assertThatCode(() -> interceptor.preSend(MessageBuilder.createMessage(new byte[0], permitido.getMessageHeaders()), null))
                .doesNotThrowAnyException();
    }

    @Test
    void tokenDesconocidoYPublicacionDesdePantallaSeRechazan() {
        when(locales.buscarPorTokenPantalla("invalido")).thenReturn(Optional.empty());
        var conectar = StompHeaderAccessor.create(StompCommand.CONNECT);
        conectar.setNativeHeader("tokenPantalla", "invalido"); conectar.setLeaveMutable(true);
        assertThatThrownBy(() -> interceptor.preSend(MessageBuilder.createMessage(new byte[0], conectar.getMessageHeaders()), null))
                .isInstanceOf(MessagingException.class);
        var enviar = StompHeaderAccessor.create(StompCommand.SEND);
        enviar.setUser(new PrincipalDePantalla(7L)); enviar.setLeaveMutable(true);
        assertThatThrownBy(() -> interceptor.preSend(MessageBuilder.createMessage(new byte[0], enviar.getMessageHeaders()), null))
                .isInstanceOf(MessagingException.class);
    }
}
