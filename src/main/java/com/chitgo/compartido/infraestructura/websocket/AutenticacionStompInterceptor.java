package com.chitgo.compartido.infraestructura.websocket;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.chitgo.pedidos.dominio.puertos.ClienteSesionRepository;
import com.chitgo.usuarios.aplicacion.AutorizadorDePermisos;
import com.chitgo.usuarios.dominio.TokenInvalidoException;
import com.chitgo.usuarios.dominio.UsuarioAutenticado;
import com.chitgo.usuarios.infraestructura.seguridad.AutenticadorPorToken;

/**
 * Autentica el CONNECT y valida cada SUBSCRIBE. El personal entra con el mismo JWT de la cookie que el HTTP
 * (AutenticadorPorToken); el cliente anónimo, con su tokenSesion en el frame CONNECT. El servidor nunca confía
 * en lo que el cliente dice: el localId del topic tiene que ser el del token y el usuario tiene que tener el
 * permiso de ese topic.
 */
@Component
public class AutenticacionStompInterceptor implements ChannelInterceptor {

    static final String ATRIBUTO_JWT = "chitgo.jwt";
    static final String HEADER_TOKEN_SESION = "tokenSesion";
    static final String DESTINO_CLIENTE = "/user/queue/pedido";
    private static final Pattern TOPIC_DE_LOCAL = Pattern.compile("^/topic/local/(\\d+)/(.+)$");

    private final AutenticadorPorToken autenticadorDelPersonal;
    private final ClienteSesionRepository sesiones;
    private final AutorizadorDePermisos autorizador;

    public AutenticacionStompInterceptor(AutenticadorPorToken autenticadorDelPersonal,
            ClienteSesionRepository sesiones, AutorizadorDePermisos autorizador) {
        this.autenticadorDelPersonal = autenticadorDelPersonal;
        this.sesiones = sesiones;
        this.autorizador = autorizador;
    }

    @Override
    public Message<?> preSend(Message<?> mensaje, MessageChannel canal) {
        StompHeaderAccessor acc = MessageHeaderAccessor.getAccessor(mensaje, StompHeaderAccessor.class);
        if (acc == null || acc.getCommand() == null) {
            return mensaje;
        }
        StompCommand comando = acc.getCommand();
        if (StompCommand.CONNECT.equals(comando)) {
            acc.setUser(autenticar(acc));
        } else if (StompCommand.SUBSCRIBE.equals(comando)) {
            validarSuscripcion(acc.getUser(), acc.getDestination());
        } else if (StompCommand.SEND.equals(comando)) {
            // Los clientes solo escuchan: todo lo que se publica sale del servidor.
            throw new MessagingException("No se puede publicar desde el cliente");
        }
        return mensaje;
    }

    private Principal autenticar(StompHeaderAccessor acc) {
        String tokenSesion = acc.getFirstNativeHeader(HEADER_TOKEN_SESION);
        if (tokenSesion != null) {
            return sesiones.buscarPorToken(tokenSesion)
                    .map(s -> new PrincipalDeCliente(s.getTokenSesion(), s.getLocalId()))
                    .orElseThrow(() -> new MessagingException("Sesión de cliente inválida"));
        }
        Map<String, Object> atributos = acc.getSessionAttributes();
        String jwt = atributos == null ? null : (String) atributos.get(ATRIBUTO_JWT);
        if (jwt == null) {
            throw new MessagingException("No autenticado");
        }
        try {
            return autenticadorDelPersonal.autenticar(jwt);
        } catch (TokenInvalidoException e) {
            throw new MessagingException("Sesión inválida o vencida");
        }
    }

    private void validarSuscripcion(Principal usuario, String destino) {
        if (destino == null) {
            throw new MessagingException("Falta el destino");
        }
        if (usuario instanceof PrincipalDeCliente) {
            if (!DESTINO_CLIENTE.equals(destino)) {
                throw new MessagingException("Destino no permitido");
            }
            return;
        }
        if (usuario instanceof Authentication autenticacion
                && autenticacion.getPrincipal() instanceof UsuarioAutenticado personal) {
            validarTopicDelPersonal(personal, destino);
            return;
        }
        throw new MessagingException("No autenticado");
    }

    private void validarTopicDelPersonal(UsuarioAutenticado personal, String destino) {
        Matcher m = TOPIC_DE_LOCAL.matcher(destino);
        if (!m.matches()) {
            throw new MessagingException("Destino no permitido");
        }
        // El Gerente/Dueño no tiene local fijo (localId nulo) y no opera: no se suscribe a los topics de un local.
        if (personal.localId() == null || !personal.localId().equals(Long.valueOf(m.group(1)))) {
            throw new MessagingException("Ese topic es de otro local");
        }
        List<String> requeridos = PermisosDeTopic.requeridos(m.group(2));
        if (requeridos.isEmpty() || !autorizador.tieneAlguno(personal, requeridos.toArray(String[]::new))) {
            throw new MessagingException("No tenés permiso para este topic");
        }
    }
}
