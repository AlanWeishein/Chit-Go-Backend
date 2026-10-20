package com.chitgo.pedidos.infraestructura.web;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chitgo.menu.dominio.Carta;
import com.chitgo.pedidos.aplicacion.EntrarALaMesaService;
import com.chitgo.pedidos.aplicacion.dto.EstadoDeMesaDto;
import com.chitgo.pedidos.aplicacion.dto.ResultadoDeSesion;
import com.chitgo.pedidos.aplicacion.dto.SesionDeMesaDto;

/**
 * HU-01. Públicos: el cliente no tiene usuario, lo identifica el token del QR. Sin lógica de negocio.
 * Ojo: los POST pasan por el CSRF de SecurityConfig, así que la app tiene que mandar el header X-XSRF-TOKEN.
 */
@RestController
@RequestMapping("/api/v1/public/mesas/{tokenQr}")
public class MesaController {

    private final EntrarALaMesaService entrarALaMesa;

    public MesaController(EntrarALaMesaService entrarALaMesa) {
        this.entrarALaMesa = entrarALaMesa;
    }

    @GetMapping
    public EstadoDeMesaDto mesa(@PathVariable("tokenQr") String tokenQr) {
        return entrarALaMesa.consultar(tokenQr);
    }

    /** 201 si se creó una sesión, 200 si el celular ya tenía una vigente y se le devuelve la misma. */
    @PostMapping("/sesiones")
    public ResponseEntity<SesionDeMesaDto> abrirSesion(@PathVariable("tokenQr") String tokenQr,
            @RequestHeader(value = "X-Session-Token", required = false) String tokenSesion) {
        ResultadoDeSesion resultado = entrarALaMesa.abrirSesion(tokenQr, tokenSesion);
        HttpStatus estado = resultado.nueva() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(estado).body(resultado.sesion());
    }

    @GetMapping("/menu")
    public Carta menu(@PathVariable("tokenQr") String tokenQr,
            @RequestParam(name = "etiqueta", required = false) List<String> etiquetas) {
        Set<String> filtro = etiquetas == null ? Set.of() : new LinkedHashSet<>(etiquetas);
        return entrarALaMesa.obtenerCarta(tokenQr, filtro);
    }
}
