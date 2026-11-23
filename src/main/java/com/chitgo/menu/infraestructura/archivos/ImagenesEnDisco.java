package com.chitgo.menu.infraestructura.archivos;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.chitgo.compartido.dominio.SolicitudInvalidaException;
import com.chitgo.menu.dominio.FormatoDeImagen;
import com.chitgo.menu.dominio.puertos.ImagenesDePlatos;

/**
 * Las fotos de los platos en una carpeta del server: {@code chitgo.imagenes.carpeta}, que en la compu apunta a
 * ./imagenes y en Docker a la carpeta del volumen (ST-29). La carpeta se crea con la primera foto.
 */
@Component
class ImagenesEnDisco implements ImagenesDePlatos {

    /**
     * Ancho y alto máximos, mirados antes de decodificar: una imagen chica comprimida puede ocupar muchísima memoria
     * al abrirla. Una foto de celular de 12 MP mide 4032 × 3024; a 6000 × 6000 ocupa unos 140 MB abierta.
     */
    static final int LADO_MAXIMO = 6000;
    static final String MIDE_DE_MAS = "La imagen mide más de " + LADO_MAXIMO + " px de ancho o de alto";
    static final String NO_SE_PUDO_ABRIR = "No se pudo abrir la imagen";

    /** Lo único que se lee o se borra: el UUID que pone guardar y la extensión. Sin barras ni puntos de más. */
    private static final Pattern NOMBRE_GUARDADO =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)");
    private static final float CALIDAD_JPEG = 0.9f;
    private static final Logger LOG = LoggerFactory.getLogger(ImagenesEnDisco.class);

    private final Path carpeta;

    ImagenesEnDisco(@Value("${chitgo.imagenes.carpeta:./imagenes}") String carpeta) {
        this.carpeta = Path.of(carpeta).toAbsolutePath().normalize();
    }

    @Override
    public String guardar(byte[] contenido, FormatoDeImagen formato) {
        byte[] limpia = codificar(abrir(contenido, formato), formato);
        String nombre = UUID.randomUUID() + "." + formato.getExtension();
        Path destino = carpeta.resolve(nombre);
        try {
            Files.createDirectories(carpeta);
            Files.write(destino, limpia, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la imagen en " + carpeta, e);
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int estado) {
                    if (estado != STATUS_COMMITTED) {
                        borrarYa(nombre);
                    }
                }
            });
        }
        return nombre;
    }

    @Override
    public void borrar(String nombre) {
        if (!NOMBRE_GUARDADO.matcher(nombre).matches()) {
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    borrarYa(nombre);
                }
            });
        } else {
            borrarYa(nombre);
        }
    }

    @Override
    public Optional<Imagen> leer(String nombre) {
        if (nombre == null || !NOMBRE_GUARDADO.matcher(nombre).matches()) {
            return Optional.empty();
        }
        FormatoDeImagen formato = FormatoDeImagen.deLaExtension(nombre.substring(nombre.lastIndexOf('.') + 1))
                .orElseThrow();
        try {
            return Optional.of(new Imagen(Files.readAllBytes(carpeta.resolve(nombre)), formato));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la imagen " + nombre, e);
        }
    }

    /** Si no se puede borrar, queda el archivo suelto y un aviso en el log: el plato ya tiene la foto nueva. */
    private void borrarYa(String nombre) {
        try {
            Files.deleteIfExists(carpeta.resolve(nombre));
        } catch (IOException e) {
            LOG.warn("No se pudo borrar la imagen {}: {}", nombre, e.getMessage());
        }
    }

    /**
     * Con el lector del formato que dicen los primeros bytes, no con el que elegiría ImageIO. Primero el ancho y el
     * alto, que salen de la cabecera; después la decodifica, y si es JPEG la endereza según el EXIF. Todo en memoria:
     * ImageIO.createImageInputStream usaría un archivo temporal.
     */
    private static BufferedImage abrir(byte[] contenido, FormatoDeImagen formato) {
        Iterator<ImageReader> lectores = ImageIO.getImageReadersByFormatName(formato.getNombreEnImageIo());
        if (!lectores.hasNext()) {
            throw new IllegalStateException("ImageIO no tiene lector de " + formato);
        }
        ImageReader lector = lectores.next();
        try (ImageInputStream entrada = new MemoryCacheImageInputStream(new ByteArrayInputStream(contenido))) {
            lector.setInput(entrada, false, false);
            if (lector.getWidth(0) > LADO_MAXIMO || lector.getHeight(0) > LADO_MAXIMO) {
                throw new SolicitudInvalidaException(MIDE_DE_MAS);
            }
            int orientacion = formato == FormatoDeImagen.JPEG ? orientacion(lector) : OrientacionExif.NORMAL;
            return OrientacionExif.aplicar(lector.read(0), orientacion);
        } catch (SolicitudInvalidaException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // Cortada, con la firma bien y el resto basura, o un JPEG en CMYK, que ImageIO no lee.
            throw new SolicitudInvalidaException(NO_SE_PUDO_ABRIR);
        } finally {
            lector.dispose();
        }
    }

    /** Una metadata que ImageIO no entiende no impide abrir la foto: queda sin girar. */
    private static int orientacion(ImageReader lector) {
        try {
            IIOMetadata metadata = lector.getImageMetadata(0);
            return OrientacionExif.leer(metadata);
        } catch (IOException | RuntimeException e) {
            return OrientacionExif.NORMAL;
        }
    }

    /** En el mismo formato y sin metadata: el IIOImage va sin la de la imagen original. */
    private static byte[] codificar(BufferedImage imagen, FormatoDeImagen formato) {
        ImageWriter escritor = ImageIO.getImageWritersByFormatName(formato.getNombreEnImageIo()).next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream salida = new MemoryCacheImageOutputStream(bytes)) {
            escritor.setOutput(salida);
            ImageWriteParam parametros = escritor.getDefaultWriteParam();
            BufferedImage aEscribir = imagen;
            if (formato == FormatoDeImagen.JPEG) {
                parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                parametros.setCompressionQuality(CALIDAD_JPEG);
                aEscribir = sinTransparencia(imagen);
            }
            escritor.write(null, new IIOImage(aEscribir, null, null), parametros);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo codificar la imagen", e);
        } finally {
            escritor.dispose();
        }
        return bytes.toByteArray();
    }

    /** El escritor de JPEG no acepta transparencia ni un tipo propio: se pasa a RGB común. */
    private static BufferedImage sinTransparencia(BufferedImage imagen) {
        int tipo = imagen.getType();
        if (tipo == BufferedImage.TYPE_3BYTE_BGR || tipo == BufferedImage.TYPE_INT_RGB
                || tipo == BufferedImage.TYPE_BYTE_GRAY) {
            return imagen;
        }
        BufferedImage rgb = new BufferedImage(imagen.getWidth(), imagen.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.drawImage(imagen, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }
}
