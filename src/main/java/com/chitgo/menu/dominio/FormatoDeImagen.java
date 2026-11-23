package com.chitgo.menu.dominio;

import java.util.Arrays;
import java.util.Optional;

/**
 * Los formatos de la foto de un plato (ST-29, decidido el 17/11): los dos que Java lee y escribe sin librerías.
 * Se reconocen por los primeros bytes del archivo, no por la extensión ni por el Content-Type, que los elige
 * quien sube el archivo.
 */
public enum FormatoDeImagen {

    JPEG("jpg", "image/jpeg", "jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
    PNG("png", "image/png", "png", new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});

    private final String extension;
    private final String tipoMime;
    private final String nombreEnImageIo;
    private final byte[] firma;

    FormatoDeImagen(String extension, String tipoMime, String nombreEnImageIo, byte[] firma) {
        this.extension = extension;
        this.tipoMime = tipoMime;
        this.nombreEnImageIo = nombreEnImageIo;
        this.firma = firma;
    }

    /** Vacío si los primeros bytes no son los de ninguno, o si el archivo es más corto que la firma. */
    public static Optional<FormatoDeImagen> detectar(byte[] contenido) {
        return Arrays.stream(values()).filter(f -> f.empiezaCon(contenido)).findFirst();
    }

    /** El de la extensión del nombre guardado, como "jpg" o "png". */
    public static Optional<FormatoDeImagen> deLaExtension(String extension) {
        return Arrays.stream(values()).filter(f -> f.extension.equals(extension)).findFirst();
    }

    private boolean empiezaCon(byte[] contenido) {
        return contenido != null && contenido.length >= firma.length
                && Arrays.equals(contenido, 0, firma.length, firma, 0, firma.length);
    }

    public String getExtension() {
        return extension;
    }

    public String getTipoMime() {
        return tipoMime;
    }

    /** El nombre con el que ImageIO busca el lector y el escritor. */
    public String getNombreEnImageIo() {
        return nombreEnImageIo;
    }
}
