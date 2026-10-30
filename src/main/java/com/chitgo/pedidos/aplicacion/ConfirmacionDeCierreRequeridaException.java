package com.chitgo.pedidos.aplicacion;

import java.util.List;

import com.chitgo.pedidos.aplicacion.dto.PedidoSinServirDto;
import com.chitgo.pedidos.dominio.MotivoDeConfirmacion;

/**
 * Caja tiene que confirmar el cierre: nadie pidió la cuenta, hay pedidos sin servir o los dos. Responde 409 con el
 * {@code codigo} {@value #CODIGO}. No hereda de ReglaDeNegocioException a propósito: el ManejadorDeErrores también
 * la agarraría, y qué advice gana dependería del orden de los beans.
 */
public class ConfirmacionDeCierreRequeridaException extends RuntimeException {

    public static final String CODIGO = "CONFIRMAR_CIERRE";

    private final List<MotivoDeConfirmacion> motivos;
    private final List<PedidoSinServirDto> pedidosSinServir;

    public ConfirmacionDeCierreRequeridaException(List<MotivoDeConfirmacion> motivos,
            List<PedidoSinServirDto> pedidosSinServir) {
        super(motivos.stream().map(MotivoDeConfirmacion::mensaje).reduce((a, b) -> a + " " + b).orElse(""));
        this.motivos = List.copyOf(motivos);
        this.pedidosSinServir = List.copyOf(pedidosSinServir);
    }

    public List<MotivoDeConfirmacion> motivos() {
        return motivos;
    }

    public List<PedidoSinServirDto> pedidosSinServir() {
        return pedidosSinServir;
    }
}
