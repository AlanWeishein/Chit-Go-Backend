package com.chitgo.pedidos.infraestructura.web;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

class QrDeMesaTest {
    @Test
    void unLectorRecuperaSolamenteElTokenDelPng() throws Exception {
        String token = "d4964b6b-fdae-4b4f-83e9-1c1e78a21723";
        var imagen = ImageIO.read(new ByteArrayInputStream(QrDeMesa.png(token)));
        var fuente = new RGBLuminanceSource(imagen.getWidth(), imagen.getHeight(),
                imagen.getRGB(0, 0, imagen.getWidth(), imagen.getHeight(), null, 0, imagen.getWidth()));
        var resultado = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(fuente)));
        assertThat(resultado.getText()).isEqualTo(token);
    }
}
