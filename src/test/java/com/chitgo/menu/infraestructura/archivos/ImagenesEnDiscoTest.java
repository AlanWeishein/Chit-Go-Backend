package com.chitgo.menu.infraestructura.archivos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.CRC32;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.chitgo.compartido.dominio.SolicitudInvalidaException;
import com.chitgo.menu.dominio.FormatoDeImagen;
import com.chitgo.menu.dominio.puertos.ImagenesDePlatos.Imagen;
import com.chitgo.pruebas.ImagenesDePrueba;

/** La carpeta es temporal: JUnit la borra al terminar cada test. */
class ImagenesEnDiscoTest {

    @TempDir
    Path raiz;

    private Path carpeta;
    private ImagenesEnDisco imagenes;

    @BeforeEach
    void preparar() {
        // Una subcarpeta que no existe: la crea guardar.
        carpeta = raiz.resolve("imagenes");
        imagenes = new ImagenesEnDisco(carpeta.toString());
    }

    @AfterEach
    void sinTransaccion() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void un_jpeg_valido_se_guarda_con_un_uuid_y_se_lee_igual() throws IOException {
        String nombre = imagenes.guardar(ImagenesDePrueba.jpeg(40, 20), FormatoDeImagen.JPEG);

        assertTrue(nombre.matches("[0-9a-f-]{36}\\.jpg"), nombre);
        assertEquals(List.of(nombre), archivos());
        Imagen leida = imagenes.leer(nombre).orElseThrow();
        assertEquals(FormatoDeImagen.JPEG, leida.formato());
        assertTrue(Arrays.equals(Files.readAllBytes(carpeta.resolve(nombre)), leida.contenido()));
        BufferedImage abierta = ImagenesDePrueba.abrir(leida.contenido());
        assertEquals(40, abierta.getWidth());
        assertEquals(20, abierta.getHeight());
    }

    @Test
    void un_png_valido_se_guarda_como_png_y_conserva_la_transparencia() {
        String nombre = imagenes.guardar(ImagenesDePrueba.png(30, 30), FormatoDeImagen.PNG);

        assertTrue(nombre.endsWith(".png"));
        byte[] guardado = imagenes.leer(nombre).orElseThrow().contenido();
        assertEquals(Optional.of(FormatoDeImagen.PNG), FormatoDeImagen.detectar(guardado));
        assertTrue(ImagenesDePrueba.abrir(guardado).getColorModel().hasAlpha());
    }

    @Test
    void un_jpeg_con_exif_se_guarda_sin_el_exif() {
        byte[] delCelular = ImagenesDePrueba.conExif(ImagenesDePrueba.jpeg(40, 20), 1, "Calle Falsa 123, -34.60 -58.38");
        assertTrue(ImagenesDePrueba.marcadores(delCelular).contains(ImagenesDePrueba.APP1));

        String nombre = imagenes.guardar(delCelular, FormatoDeImagen.JPEG);

        byte[] guardado = imagenes.leer(nombre).orElseThrow().contenido();
        assertFalse(ImagenesDePrueba.marcadores(guardado).contains(ImagenesDePrueba.APP1));
        String comoTexto = new String(guardado, StandardCharsets.ISO_8859_1);
        assertFalse(comoTexto.contains("Exif"));
        assertFalse(comoTexto.contains("Calle Falsa"));
    }

    @Test
    void un_jpeg_con_orientacion_6_se_guarda_girado_90_grados_a_la_derecha() {
        // Rojo a la izquierda y azul a la derecha: girada a la derecha, el rojo queda arriba.
        byte[] acostada = ImagenesDePrueba.conExif(ImagenesDePrueba.jpeg(80, 40), 6, "");

        BufferedImage guardada = ImagenesDePrueba.abrir(
                imagenes.leer(imagenes.guardar(acostada, FormatoDeImagen.JPEG)).orElseThrow().contenido());

        assertEquals(40, guardada.getWidth());
        assertEquals(80, guardada.getHeight());
        assertTrue(ImagenesDePrueba.esRojo(guardada, 20, 15));
        assertTrue(ImagenesDePrueba.esAzul(guardada, 20, 65));
    }

    @Test
    void un_jpeg_con_orientacion_8_se_guarda_girado_a_la_izquierda() {
        byte[] acostada = ImagenesDePrueba.conExif(ImagenesDePrueba.jpeg(80, 40), 8, "");

        BufferedImage guardada = ImagenesDePrueba.abrir(
                imagenes.leer(imagenes.guardar(acostada, FormatoDeImagen.JPEG)).orElseThrow().contenido());

        assertEquals(40, guardada.getWidth());
        assertEquals(80, guardada.getHeight());
        assertTrue(ImagenesDePrueba.esAzul(guardada, 20, 15));
        assertTrue(ImagenesDePrueba.esRojo(guardada, 20, 65));
    }

    @Test
    void una_imagen_de_mas_de_6000_px_da_400_sin_decodificarla() {
        // Solo la firma y la cabecera IHDR, sin los datos: si la decodificara, daría "No se pudo abrir".
        byte[] soloCabecera = cabeceraPng(10000, 10);

        SolicitudInvalidaException e = assertThrows(SolicitudInvalidaException.class,
                () -> imagenes.guardar(soloCabecera, FormatoDeImagen.PNG));

        assertEquals("La imagen mide más de 6000 px de ancho o de alto", e.getMessage());
        assertFalse(Files.exists(carpeta));
    }

    @Test
    void la_firma_bien_y_el_resto_basura_da_400_y_no_guarda() {
        byte[] jpeg = ImagenesDePrueba.jpeg(40, 20);
        byte[] cortada = Arrays.copyOf(jpeg, 3 + 40);
        Arrays.fill(cortada, 3, cortada.length, (byte) 0x41);

        SolicitudInvalidaException e = assertThrows(SolicitudInvalidaException.class,
                () -> imagenes.guardar(cortada, FormatoDeImagen.JPEG));

        assertEquals("No se pudo abrir la imagen", e.getMessage());
        assertFalse(Files.exists(carpeta));
    }

    @Test
    void leer_solo_acepta_un_uuid_con_la_extension() throws IOException {
        Files.createDirectories(carpeta);
        Files.writeString(raiz.resolve("application.yml"), "secreto");
        Files.writeString(carpeta.resolve("menu.jpg"), "no es un uuid");

        assertEquals(Optional.empty(), imagenes.leer("../application.yml"));
        assertEquals(Optional.empty(), imagenes.leer("..%2Fapplication.yml"));
        assertEquals(Optional.empty(), imagenes.leer("menu.jpg"));
        assertEquals(Optional.empty(), imagenes.leer("3f2a1b4c-0000-4000-8000-000000000000.gif"));
        assertEquals(Optional.empty(), imagenes.leer("3F2A1B4C-0000-4000-8000-000000000000.jpg"));
        assertEquals(Optional.empty(), imagenes.leer(null));
    }

    @Test
    void un_nombre_bien_formado_que_no_existe_da_vacio() {
        assertEquals(Optional.empty(), imagenes.leer("3f2a1b4c-0000-4000-8000-000000000000.jpg"));
    }

    @Test
    void sin_transaccion_borrar_borra_en_el_momento_y_un_nombre_raro_no_toca_nada() throws IOException {
        String nombre = imagenes.guardar(ImagenesDePrueba.jpeg(10, 10), FormatoDeImagen.JPEG);
        Files.writeString(raiz.resolve("application.yml"), "secreto");

        imagenes.borrar("../application.yml");
        imagenes.borrar(nombre);

        assertEquals(List.of(), archivos());
        assertTrue(Files.exists(raiz.resolve("application.yml")));
    }

    @Test
    void con_transaccion_la_vieja_se_borra_recien_despues_del_commit() throws IOException {
        String vieja = imagenes.guardar(ImagenesDePrueba.jpeg(10, 10), FormatoDeImagen.JPEG);
        empezarTransaccion();

        String nueva = imagenes.guardar(ImagenesDePrueba.jpeg(10, 10), FormatoDeImagen.JPEG);
        imagenes.borrar(vieja);
        assertEquals(2, archivos().size());

        terminar(TransactionSynchronization.STATUS_COMMITTED);
        assertEquals(List.of(nueva), archivos());
    }

    @Test
    void si_la_transaccion_se_deshace_la_nueva_se_borra_y_la_vieja_sigue() throws IOException {
        String vieja = imagenes.guardar(ImagenesDePrueba.jpeg(10, 10), FormatoDeImagen.JPEG);
        empezarTransaccion();

        imagenes.guardar(ImagenesDePrueba.jpeg(10, 10), FormatoDeImagen.JPEG);
        imagenes.borrar(vieja);

        terminar(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertEquals(List.of(vieja), archivos());
    }

    private List<String> archivos() throws IOException {
        if (!Files.exists(carpeta)) {
            return List.of();
        }
        try (Stream<Path> lista = Files.list(carpeta)) {
            return lista.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    private static void empezarTransaccion() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    /** Lo que hace el TransactionManager al terminar: afterCommit solo si hubo commit, afterCompletion siempre. */
    private static void terminar(int estado) {
        List<TransactionSynchronization> sincronizaciones = TransactionSynchronizationManager.getSynchronizations();
        if (estado == TransactionSynchronization.STATUS_COMMITTED) {
            sincronizaciones.forEach(TransactionSynchronization::afterCommit);
        }
        sincronizaciones.forEach(s -> s.afterCompletion(estado));
    }

    /** La firma de PNG y el chunk IHDR (ancho, alto, 8 bits, RGB), con su CRC, y nada más. */
    private static byte[] cabeceraPng(int ancho, int alto) {
        ByteBuffer ihdr = ByteBuffer.allocate(4 + 13);
        ihdr.put("IHDR".getBytes(StandardCharsets.US_ASCII)).putInt(ancho).putInt(alto)
                .put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0);
        CRC32 crc = new CRC32();
        crc.update(ihdr.array());
        ByteBuffer png = ByteBuffer.allocate(8 + 4 + 17 + 4);
        png.put(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'})
                .putInt(13).put(ihdr.array()).putInt((int) crc.getValue());
        return png.array();
    }
}
