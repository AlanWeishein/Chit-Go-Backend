package com.chitgo.usuarios.infraestructura.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.chitgo.usuarios.dominio.PinIncorrectoException;
import com.chitgo.usuarios.dominio.UsuarioBloqueadoException;

/**
 * Los errores del login, en ProblemDetail, con los mensajes de HU-04. Van acá y no en ManejadorDeErrores para
 * que compartido no dependa de las excepciones de usuarios.
 */
@RestControllerAdvice
class ErroresDeIngreso {

    @ExceptionHandler(PinIncorrectoException.class)
    ProblemDetail pinIncorrecto(PinIncorrectoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(UsuarioBloqueadoException.class)
    ProblemDetail bloqueado(UsuarioBloqueadoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.LOCKED, e.getMessage());
    }
}
