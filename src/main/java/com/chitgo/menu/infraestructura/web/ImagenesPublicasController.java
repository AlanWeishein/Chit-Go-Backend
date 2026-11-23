package com.chitgo.menu.infraestructura.web;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.chitgo.compartido.dominio.NoEncontradoException;
import com.chitgo.menu.dominio.puertos.ImagenesDePlatos;
import com.chitgo.menu.dominio.puertos.ImagenesDePlatos.Imagen;

/**
 * ST-29: las fotos de los platos, para la carta del cliente y la del Admin. Público, como todo /api/v1/public/**
 * en SecurityConfig. Sin lógica: el nombre lo valida ImagenesDePlatos (un UUID y la extensión), así que
 * ../application.yml o cualquier otro nombre da el mismo 404 que una foto que no existe.
 */
@RestController
public class ImagenesPublicasController {

    static final String NO_EXISTE = "La imagen no existe";

    /** Cada foto nueva tiene un nombre nuevo y un nombre no se reusa: el navegador la puede guardar un año. */
    private static final CacheControl UN_ANIO = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();

    private final ImagenesDePlatos imagenes;

    public ImagenesPublicasController(ImagenesDePlatos imagenes) {
        this.imagenes = imagenes;
    }

    @GetMapping(ImagenesDePlatos.RUTA_PUBLICA + "{nombre}")
    public ResponseEntity<byte[]> ver(@PathVariable("nombre") String nombre) {
        Imagen imagen = imagenes.leer(nombre).orElseThrow(() -> new NoEncontradoException(NO_EXISTE));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(imagen.formato().getTipoMime()))
                .cacheControl(UN_ANIO)
                .body(imagen.contenido());
    }
}
