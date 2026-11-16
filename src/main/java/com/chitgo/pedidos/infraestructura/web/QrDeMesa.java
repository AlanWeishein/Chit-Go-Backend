package com.chitgo.pedidos.infraestructura.web;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;

/** PNG sin red; reimprimir no cambia el token. El contenido es únicamente el UUID de la mesa. */
final class QrDeMesa {
    private QrDeMesa() { }

    static byte[] png(String token) {
        try {
            var matriz = new QRCodeWriter().encode(token, BarcodeFormat.QR_CODE, 512, 512);
            var imagen = new BufferedImage(512, 512, BufferedImage.TYPE_BYTE_BINARY);
            for (int y = 0; y < 512; y++) {
                for (int x = 0; x < 512; x++) imagen.setRGB(x, y, matriz.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
            }
            var salida = new ByteArrayOutputStream();
            ImageIO.write(imagen, "png", salida);
            return salida.toByteArray();
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("No se pudo generar el QR", e);
        }
    }
}
