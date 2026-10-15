package com.chitgo.compartido.infraestructura.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.chitgo.compartido.dominio.SinSesionException;

/**
 * Pasa las excepciones a ProblemDetail (RFC 9457). Los 401 y 403 de Spring Security también llegan acá:
 * SecurityConfig se los deriva desde el filtro.
 */
@RestControllerAdvice
public class ManejadorDeErrores {

    static final String SIN_SESION = "No hay una sesión iniciada, o venció.";
    static final String SIN_PERMISO = "No tenés permiso para hacer esto.";

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail sinAutenticar(AuthenticationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, SIN_SESION);
    }

    @ExceptionHandler(SinSesionException.class)
    ProblemDetail sinSesion(SinSesionException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, SIN_SESION);
    }

    /** También el token CSRF que falta o no coincide. */
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail sinPermiso(AccessDeniedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, SIN_PERMISO);
    }
}
