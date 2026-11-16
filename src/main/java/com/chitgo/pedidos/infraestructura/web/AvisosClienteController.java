package com.chitgo.pedidos.infraestructura.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.chitgo.pedidos.aplicacion.AvisosClienteService;
import com.chitgo.pedidos.aplicacion.dto.AvisoDto;

@RestController
@RequestMapping("/api/v1/public")
public class AvisosClienteController {
    private final AvisosClienteService servicio;

    public AvisosClienteController(AvisosClienteService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/avisos/llamar-mozo")
    @ResponseStatus(HttpStatus.CREATED)
    public AvisoDto llamar(@RequestHeader(value = "X-Session-Token", required = false) String token) {
        return servicio.llamarMozo(token);
    }

    @PostMapping("/cuenta/solicitar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void solicitar(@RequestHeader(value = "X-Session-Token", required = false) String token) {
        servicio.solicitarCuenta(token);
    }
}
