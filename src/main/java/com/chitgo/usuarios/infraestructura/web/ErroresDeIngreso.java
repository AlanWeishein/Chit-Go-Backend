package com.chitgo.usuarios.infraestructura.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.chitgo.usuarios.dominio.ContrasenaRequeridaException;
import com.chitgo.usuarios.dominio.CredencialNuevaInvalidaException;
import com.chitgo.usuarios.dominio.CredencialProvisoriaException;
import com.chitgo.usuarios.dominio.CredencialYaElegidaException;
import com.chitgo.usuarios.dominio.CredencialesIncorrectasException;
import com.chitgo.usuarios.dominio.PinIncorrectoException;
import com.chitgo.usuarios.dominio.UsuarioBloqueadoException;
import com.chitgo.usuarios.dominio.UsuarioOContrasenaIncorrectosException;

/**
 * Los errores del login y del primer ingreso, en ProblemDetail, con los mensajes de HU-04. Van acá y no en ManejadorDeErrores para
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

    /** Login con email: también el bloqueado da 401, no 423. */
    @ExceptionHandler(CredencialesIncorrectasException.class)
    ProblemDetail credencialesIncorrectas(CredencialesIncorrectasException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    /** Usuario y contraseña del mozo: como el de email, también el bloqueado da 401. */
    @ExceptionHandler(UsuarioOContrasenaIncorrectosException.class)
    ProblemDetail usuarioOContrasenaIncorrectos(UsuarioOContrasenaIncorrectosException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    /** El campo "codigo" es lo que el front compara para mostrar P-32, en vez del texto del detail. */
    @ExceptionHandler(ContrasenaRequeridaException.class)
    ProblemDetail contrasenaRequerida(ContrasenaRequeridaException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
        problema.setProperty("codigo", ContrasenaRequeridaException.CODIGO);
        return problema;
    }

    /** Primer ingreso: como en P-32, el front compara el "codigo" para mostrar P-29, P-30 o P-31. */
    @ExceptionHandler(CredencialProvisoriaException.class)
    ProblemDetail credencialProvisoria(CredencialProvisoriaException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
        problema.setProperty("codigo", CredencialProvisoriaException.CODIGO);
        return problema;
    }

    @ExceptionHandler(CredencialNuevaInvalidaException.class)
    ProblemDetail credencialNuevaInvalida(CredencialNuevaInvalidaException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(CredencialYaElegidaException.class)
    ProblemDetail credencialYaElegida(CredencialYaElegidaException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }
}
