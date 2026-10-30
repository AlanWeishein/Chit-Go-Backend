package com.chitgo.pedidos.infraestructura.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.chitgo.pedidos.aplicacion.ConfirmacionDeCierreRequeridaException;
import com.chitgo.pedidos.dominio.MotivoDeConfirmacion;

/**
 * El 409 que pide confirmar el cierre de la cuenta (HU-10). Va acá y no en ManejadorDeErrores, como
 * ErroresDeIngreso: compartido no tiene que conocer esta excepción.
 */
@RestControllerAdvice
class ErroresDeCierre {

    /**
     * El front compara el "codigo" y pinta desde "motivos"; el "detail" trae los mensajes de HU-10 (con dos motivos,
     * los dos juntos). "pedidosSinServir" es [] si el único motivo es que nadie pidió la cuenta.
     */
    @ExceptionHandler(ConfirmacionDeCierreRequeridaException.class)
    ProblemDetail confirmarCierre(ConfirmacionDeCierreRequeridaException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problema.setProperty("codigo", ConfirmacionDeCierreRequeridaException.CODIGO);
        problema.setProperty("motivos", e.motivos().stream().map(MotivoDeConfirmacion::name).toList());
        problema.setProperty("pedidosSinServir", e.pedidosSinServir());
        return problema;
    }
}
