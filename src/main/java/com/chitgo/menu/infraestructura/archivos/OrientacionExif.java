package com.chitgo.menu.infraestructura.archivos;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;

import org.w3c.dom.Node;

/**
 * El celular guarda la foto como la toma el sensor y anota en el EXIF cómo girarla para verla derecha (la etiqueta
 * Orientation, 0x0112). Al volver a codificar, el EXIF se va, así que el giro se aplica antes: si no, una foto
 * sacada con el celular parado sale acostada en la carta. Propuesto por Claude Code, con el OK de Eze el 23/11.
 *
 * <p>Lee solo la primera IFD del segmento APP1 "Exif" del JPEG, con la metadata nativa de ImageIO, sin librerías.
 * Cualquier cosa que no se entienda vale 1 (no girar).
 */
final class OrientacionExif {

    static final int NORMAL = 1;

    private static final String FORMATO_NATIVO_JPEG = "javax_imageio_jpeg_image_1.0";
    private static final String MARCADOR_APP1 = "225";
    private static final byte[] CABECERA_EXIF = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);
    private static final int ETIQUETA_ORIENTACION = 0x0112;

    private OrientacionExif() {
    }

    /** De 1 a 8, como el EXIF. 1 si no hay EXIF, no trae la etiqueta o está mal armado. */
    static int leer(IIOMetadata metadata) {
        if (metadata == null || !Arrays.asList(metadata.getMetadataFormatNames()).contains(FORMATO_NATIVO_JPEG)) {
            return NORMAL;
        }
        Node raiz = metadata.getAsTree(FORMATO_NATIVO_JPEG);
        for (Node secuencia = raiz.getFirstChild(); secuencia != null; secuencia = secuencia.getNextSibling()) {
            if (!"markerSequence".equals(secuencia.getNodeName())) {
                continue;
            }
            for (Node marcador = secuencia.getFirstChild(); marcador != null; marcador = marcador.getNextSibling()) {
                if (marcador instanceof IIOMetadataNode nodo && "unknown".equals(nodo.getNodeName())
                        && MARCADOR_APP1.equals(nodo.getAttribute("MarkerTag"))
                        && nodo.getUserObject() instanceof byte[] datos) {
                    int orientacion = deApp1(datos);
                    if (orientacion != NORMAL) {
                        return orientacion;
                    }
                }
            }
        }
        return NORMAL;
    }

    /** El APP1 es "Exif\0\0" y un TIFF: el orden de los bytes, 42, dónde empieza la IFD0 y sus entradas de 12 bytes. */
    static int deApp1(byte[] datos) {
        int tiff = CABECERA_EXIF.length;
        if (datos.length < tiff + 8 || !Arrays.equals(datos, 0, tiff, CABECERA_EXIF, 0, tiff)) {
            return NORMAL;
        }
        ByteBuffer b = ByteBuffer.wrap(datos);
        if (datos[tiff] == 'I' && datos[tiff + 1] == 'I') {
            b.order(ByteOrder.LITTLE_ENDIAN);
        } else if (datos[tiff] == 'M' && datos[tiff + 1] == 'M') {
            b.order(ByteOrder.BIG_ENDIAN);
        } else {
            return NORMAL;
        }
        if (b.getShort(tiff + 2) != 42) {
            return NORMAL;
        }
        long ifd0 = tiff + Integer.toUnsignedLong(b.getInt(tiff + 4));
        if (ifd0 + 2 > datos.length) {
            return NORMAL;
        }
        int entradas = Short.toUnsignedInt(b.getShort((int) ifd0));
        for (int i = 0; i < entradas; i++) {
            long entrada = ifd0 + 2 + 12L * i;
            if (entrada + 12 > datos.length) {
                return NORMAL;
            }
            if (Short.toUnsignedInt(b.getShort((int) entrada)) == ETIQUETA_ORIENTACION) {
                int valor = Short.toUnsignedInt(b.getShort((int) entrada + 8));
                return valor >= 1 && valor <= 8 ? valor : NORMAL;
            }
        }
        return NORMAL;
    }

    /**
     * La imagen como se ve: 2 a 4 la espejan o la dan vuelta; 5 a 8 además cambian el ancho por el alto (6 es girar
     * 90° a la derecha y 8, a la izquierda).
     */
    static BufferedImage aplicar(BufferedImage imagen, int orientacion) {
        if (orientacion <= NORMAL || orientacion > 8) {
            return imagen;
        }
        int w = imagen.getWidth();
        int h = imagen.getHeight();
        // AffineTransform(m00, m10, m01, m11, m02, m12): x' = m00 x + m01 y + m02 e y' = m10 x + m11 y + m12.
        AffineTransform giro = switch (orientacion) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);
            default -> new AffineTransform(0, -1, 1, 0, 0, w);
        };
        boolean cruza = orientacion >= 5;
        BufferedImage derecha = new BufferedImage(cruza ? h : w, cruza ? w : h, tipoPara(imagen));
        Graphics2D g = derecha.createGraphics();
        try {
            g.drawImage(imagen, giro, null);
        } finally {
            g.dispose();
        }
        return derecha;
    }

    /** El mismo tipo si se puede crear uno vacío; si no (TYPE_CUSTOM), RGB, con o sin transparencia. */
    private static int tipoPara(BufferedImage imagen) {
        if (imagen.getType() != BufferedImage.TYPE_CUSTOM) {
            return imagen.getType();
        }
        return imagen.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
    }
}
