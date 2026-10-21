package com.chitgo.compartido.infraestructura.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.chitgo.compartido.dominio.AccesoDenegadoException;
import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.compartido.dominio.ReglaDeNegocioException;
import com.chitgo.compartido.dominio.SinSesionException;
import com.chitgo.compartido.dominio.SolicitudInvalidaException;

/**
 * Pasa las excepciones a ProblemDetail (RFC 9457). Los 401 y 403 de Spring Security también llegan acá:
 * SecurityConfig se los deriva desde el filtro.
 */
@RestControllerAdvice
public class ManejadorDeErrores {

    static final String SIN_SESION = "No hay una sesión iniciada, o venció.";
    static final String SIN_PERMISO = "No tenés permiso para hacer esto.";
    static final String DATOS_INVALIDOS = "Los datos enviados no son válidos.";

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

    @ExceptionHandler(NoEncontradoException.class)
    ProblemDetail noEncontrado(NoEncontradoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** Falta el permiso, o el cliente no es la sesión iniciadora o el pedido es de otra sesión. */
    @ExceptionHandler(AccesoDenegadoException.class)
    ProblemDetail accesoDenegado(AccesoDenegadoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    /** Transición inválida, cuenta cerrada, plato no disponible o conflicto de concurrencia. */
    @ExceptionHandler(ReglaDeNegocioException.class)
    ProblemDetail reglaDeNegocio(ReglaDeNegocioException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /** Un dato del pedido que no cumple la regla (cantidad, largo de la observación). */
    @ExceptionHandler(SolicitudInvalidaException.class)
    ProblemDetail solicitudInvalida(SolicitudInvalidaException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** Un campo que falta o no tiene el formato (un PIN con letras), o un cuerpo que no es JSON. */
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ProblemDetail datosInvalidos(Exception e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, DATOS_INVALIDOS);
    }
}
