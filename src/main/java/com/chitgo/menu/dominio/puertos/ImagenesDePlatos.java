package com.chitgo.menu.dominio.puertos;

import java.util.Optional;

import com.chitgo.menu.dominio.FormatoDeImagen;

/**
 * Las fotos de los platos (ST-29). Lo declara menu y lo implementa la infraestructura, que las guarda en una carpeta
 * del server con un UUID de nombre (decidido el 13/11). En la base va solo la ruta.
 */
public interface ImagenesDePlatos {

    /** La ruta pública de las fotos guardadas, la que queda en plato.imagen_url antes del nombre. */
    String RUTA_PUBLICA = "/api/v1/public/imagenes/";

    /**
     * Mide la imagen sin decodificarla, la decodifica y la vuelve a codificar en el mismo formato, así se va la
     * metadata (el EXIF trae la ubicación del celular). Devuelve el nombre nuevo, como {@code <uuid>.jpg}. Si la
     * imagen mide de más o no se puede abrir, 400 y no guarda nada.
     *
     * <p>Con una transacción en curso, si la transacción se deshace el archivo nuevo se borra.
     */
    String guardar(byte[] contenido, FormatoDeImagen formato);

    /** Con una transacción en curso, borra después del commit: si se deshace, la foto vieja sigue. */
    void borrar(String nombre);

    /** Vacío si no existe o si el nombre no tiene la forma de uno guardado (un UUID y la extensión). */
    Optional<Imagen> leer(String nombre);

    record Imagen(byte[] contenido, FormatoDeImagen formato) {
    }
}
