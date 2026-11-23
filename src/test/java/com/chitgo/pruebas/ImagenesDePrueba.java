package com.chitgo.pruebas;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

/** Fotos armadas en memoria para los tests de ST-29: mitad roja a la izquierda y mitad azul a la derecha. */
public final class ImagenesDePrueba {

    public static final int APP0 = 0xE0;
    public static final int APP1 = 0xE1;

    private ImagenesDePrueba() {
    }

    public static BufferedImage dosColores(int ancho, int alto, int tipo) {
        BufferedImage imagen = new BufferedImage(ancho, alto, tipo);
        Graphics2D g = imagen.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, ancho / 2, alto);
        g.setColor(Color.BLUE);
        g.fillRect(ancho / 2, 0, ancho - ancho / 2, alto);
        g.dispose();
        return imagen;
    }

    public static byte[] jpeg(int ancho, int alto) {
        return codificar(dosColores(ancho, alto, BufferedImage.TYPE_INT_RGB), "jpeg");
    }

    public static byte[] png(int ancho, int alto) {
        return codificar(dosColores(ancho, alto, BufferedImage.TYPE_INT_ARGB), "png");
    }

    /**
     * El JPEG como lo deja un celular: en lugar del APP0 (JFIF) de ImageIO, un APP1 "Exif" con la descripción y la
     * orientación en la IFD0, en big endian.
     */
    public static byte[] conExif(byte[] jpeg, int orientacion, String descripcion) {
        byte[] texto = (descripcion + "\0").getBytes(StandardCharsets.US_ASCII);
        ByteBuffer tiff = ByteBuffer.allocate(8 + 2 + 2 * 12 + 4 + texto.length);
        tiff.put((byte) 'M').put((byte) 'M').putShort((short) 42).putInt(8);
        tiff.putShort((short) 2);
        tiff.putShort((short) 0x010E).putShort((short) 2).putInt(texto.length).putInt(8 + 2 + 2 * 12 + 4);
        tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientacion).putShort((short) 0);
        tiff.putInt(0);
        tiff.put(texto);

        byte[] exif = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);
        int largo = 2 + exif.length + tiff.capacity();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        salida.write(0xFF);
        salida.write(0xD8);
        salida.write(0xFF);
        salida.write(APP1);
        salida.write(largo >> 8);
        salida.write(largo & 0xFF);
        salida.writeBytes(exif);
        salida.writeBytes(tiff.array());
        int resto = 2;
        if ((jpeg[2] & 0xFF) == 0xFF && (jpeg[3] & 0xFF) == APP0) {
            resto = 4 + ((jpeg[4] & 0xFF) << 8 | (jpeg[5] & 0xFF));
        }
        salida.write(jpeg, resto, jpeg.length - resto);
        return salida.toByteArray();
    }

    /** Los marcadores del JPEG antes de los datos de la imagen (SOS): APP0, APP1, DQT... */
    public static List<Integer> marcadores(byte[] jpeg) {
        List<Integer> marcadores = new ArrayList<>();
        int i = 2;
        while (i + 4 <= jpeg.length && (jpeg[i] & 0xFF) == 0xFF) {
            int marcador = jpeg[i + 1] & 0xFF;
            marcadores.add(marcador);
            if (marcador == 0xDA) {
                break;
            }
            i += 2 + ((jpeg[i + 2] & 0xFF) << 8 | (jpeg[i + 3] & 0xFF));
        }
        return marcadores;
    }

    public static BufferedImage abrir(byte[] contenido) {
        try {
            return ImageIO.read(new ByteArrayInputStream(contenido));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Si el pixel es más rojo que azul. Con JPEG los colores no quedan exactos. */
    public static boolean esRojo(BufferedImage imagen, int x, int y) {
        Color c = new Color(imagen.getRGB(x, y));
        return c.getRed() > 150 && c.getBlue() < 100;
    }

    public static boolean esAzul(BufferedImage imagen, int x, int y) {
        Color c = new Color(imagen.getRGB(x, y));
        return c.getBlue() > 150 && c.getRed() < 100;
    }

    private static byte[] codificar(BufferedImage imagen, String formato) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            ImageIO.write(imagen, formato, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }
}
