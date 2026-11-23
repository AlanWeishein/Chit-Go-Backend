package com.chitgo.menu.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.chitgo.pruebas.ImagenesDePrueba;

class FormatoDeImagenTest {

    @Test
    void un_jpeg_y_un_png_se_reconocen_por_los_primeros_bytes() {
        assertEquals(Optional.of(FormatoDeImagen.JPEG), FormatoDeImagen.detectar(ImagenesDePrueba.jpeg(10, 10)));
        assertEquals(Optional.of(FormatoDeImagen.PNG), FormatoDeImagen.detectar(ImagenesDePrueba.png(10, 10)));
    }

    @Test
    void un_texto_no_es_imagen_aunque_se_llame_foto_jpg() {
        byte[] texto = "Esto no es una foto, es un texto renombrado a foto.jpg".getBytes(StandardCharsets.UTF_8);

        assertEquals(Optional.empty(), FormatoDeImagen.detectar(texto));
    }

    @Test
    void vacio_null_o_mas_corto_que_la_firma_no_es_ninguno() {
        assertEquals(Optional.empty(), FormatoDeImagen.detectar(new byte[0]));
        assertEquals(Optional.empty(), FormatoDeImagen.detectar(null));
        assertEquals(Optional.empty(), FormatoDeImagen.detectar(new byte[] {(byte) 0x89, 'P', 'N', 'G'}));
        assertEquals(Optional.empty(), FormatoDeImagen.detectar(new byte[] {(byte) 0xFF, (byte) 0xD8}));
    }

    @Test
    void la_extension_del_nombre_guardado_da_el_formato_y_el_tipo() {
        assertEquals("image/jpeg", FormatoDeImagen.deLaExtension("jpg").orElseThrow().getTipoMime());
        assertEquals("image/png", FormatoDeImagen.deLaExtension("png").orElseThrow().getTipoMime());
        assertEquals(Optional.empty(), FormatoDeImagen.deLaExtension("gif"));
    }
}
