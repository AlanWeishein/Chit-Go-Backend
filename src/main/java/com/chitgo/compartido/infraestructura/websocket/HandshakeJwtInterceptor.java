package com.chitgo.compartido.infraestructura.websocket;

import java.util.Map;

import jakarta.servlet.http.Cookie;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.chitgo.usuarios.infraestructura.seguridad.CookieDeSesion;

/**
 * El navegador no deja mandar headers propios en el handshake de un WebSocket, pero sí manda las cookies
 * (la de sesión tiene Path=/): acá se guarda el JWT del personal para usarlo después, en el CONNECT de STOMP.
 */
class HandshakeJwtInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
            Map<String, Object> atributos) {
        if (request instanceof ServletServerHttpRequest servlet && servlet.getServletRequest().getCookies() != null) {
            for (Cookie cookie : servlet.getServletRequest().getCookies()) {
                if (CookieDeSesion.NOMBRE.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                    atributos.put(AutenticacionStompInterceptor.ATRIBUTO_JWT, cookie.getValue());
                }
            }
        }
        return true; // quien no tiene credenciales se rechaza recién en el CONNECT
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
            Exception excepcion) {
    }
}
