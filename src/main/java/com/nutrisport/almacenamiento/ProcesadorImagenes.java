package com.nutrisport.almacenamiento;

import com.nutrisport.comun.excepcion.ArchivoNoValidoException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.stereotype.Component;

/**
 * Valida y reprocesa las fotos antes de guardarlas o enviarlas a terceros (§5.8 y §8.4):
 * <ol>
 * <li>Tipo por firma de bytes, no por la extensión ni el Content-Type: solo JPEG ({@code FF D8 FF}) o
 * PNG ({@code 89 50 4E 47}).</li>
 * <li>Dimensiones leídas de la cabecera, sin decodificar: más de 40 megapíxeles se rechaza, como
 * protección ante "bombas" de descompresión.</li>
 * <li>Decodificación, conversión a RGB (la transparencia pasa a fondo blanco), reducción a 1024 px de lado
 * largo como máximo (nunca se amplía) y recodificación a JPEG de calidad 0,85. Al recodificar se pierden
 * los metadatos EXIF, incluida la geolocalización, y se neutraliza el contenido no visual.</li>
 * <li>Miniatura de 320 px de lado largo.</li>
 * </ol>
 */
@Component
public class ProcesadorImagenes {

    static final long MAXIMO_PIXELES = 40_000_000L;
    static final int LADO_MAXIMO = 1024;
    static final int LADO_MINIATURA = 320;
    static final float CALIDAD_JPEG = 0.85f;

    private static final byte[] FIRMA_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] FIRMA_PNG = {(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final String NO_ES_IMAGEN = "La foto debe ser una imagen JPEG o PNG";

    public ImagenProcesada procesar(byte[] original) {
        if (!empiezaPor(original, FIRMA_JPEG) && !empiezaPor(original, FIRMA_PNG)) {
            throw new ArchivoNoValidoException(NO_ES_IMAGEN);
        }
        BufferedImage decodificada = decodificarConLimite(original);
        BufferedImage imagen = reducir(aRgb(decodificada), LADO_MAXIMO);
        BufferedImage miniatura = reducir(imagen, LADO_MINIATURA);
        return new ImagenProcesada(aJpeg(imagen), aJpeg(miniatura), imagen.getWidth(), imagen.getHeight());
    }

    // ---------------------------------------------------------------------------------------------
    // Validación y decodificación
    // ---------------------------------------------------------------------------------------------

    private static boolean empiezaPor(byte[] datos, byte[] firma) {
        if (datos == null || datos.length < firma.length) {
            return false;
        }
        for (int i = 0; i < firma.length; i++) {
            if (datos[i] != firma[i]) {
                return false;
            }
        }
        return true;
    }

    /** Lee primero solo las dimensiones de la cabecera y únicamente decodifica si no pasan del límite. */
    private static BufferedImage decodificarConLimite(byte[] original) {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(entrada);
            if (!lectores.hasNext()) {
                throw new ArchivoNoValidoException(NO_ES_IMAGEN);
            }
            ImageReader lector = lectores.next();
            try {
                lector.setInput(entrada, true, true);
                long pixeles = (long) lector.getWidth(0) * lector.getHeight(0);
                if (pixeles > MAXIMO_PIXELES) {
                    throw new ArchivoNoValidoException("La foto supera los 40 megapíxeles");
                }
                return lector.read(0);
            } finally {
                lector.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ArchivoNoValidoException archivoNoValido) {
                throw archivoNoValido;
            }
            // Imagen truncada, corrupta o en un formato interno no admitido (p. ej. JPEG CMYK)
            throw new ArchivoNoValidoException("No se ha podido leer la foto");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Transformación y codificación
    // ---------------------------------------------------------------------------------------------

    /** Copia en RGB sin canal alfa; las zonas transparentes quedan en blanco. */
    private static BufferedImage aRgb(BufferedImage imagen) {
        BufferedImage rgb = new BufferedImage(imagen.getWidth(), imagen.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D grafico = rgb.createGraphics();
        try {
            grafico.setColor(Color.WHITE);
            grafico.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            grafico.drawImage(imagen, 0, 0, null);
        } finally {
            grafico.dispose();
        }
        return rgb;
    }

    /**
     * Reduce la imagen para que su lado largo sea como mucho {@code ladoMaximo}, sin ampliarla nunca. Reduce
     * a la mitad en varios pasos y hace el último ajuste exacto, lo que da mejor calidad que un único
     * escalado bilineal grande.
     */
    private static BufferedImage reducir(BufferedImage imagen, int ladoMaximo) {
        int ladoLargo = Math.max(imagen.getWidth(), imagen.getHeight());
        if (ladoLargo <= ladoMaximo) {
            return imagen;
        }
        double escala = (double) ladoMaximo / ladoLargo;
        int anchoFinal = Math.max(1, (int) Math.round(imagen.getWidth() * escala));
        int altoFinal = Math.max(1, (int) Math.round(imagen.getHeight() * escala));

        BufferedImage actual = imagen;
        int ancho = imagen.getWidth();
        int alto = imagen.getHeight();
        while (ancho / 2 >= anchoFinal && alto / 2 >= altoFinal) {
            ancho /= 2;
            alto /= 2;
            actual = escalar(actual, ancho, alto);
        }
        return escalar(actual, anchoFinal, altoFinal);
    }

    private static BufferedImage escalar(BufferedImage imagen, int ancho, int alto) {
        BufferedImage destino = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D grafico = destino.createGraphics();
        try {
            grafico.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            grafico.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            grafico.drawImage(imagen, 0, 0, ancho, alto, null);
        } finally {
            grafico.dispose();
        }
        return destino;
    }

    /** JPEG de calidad 0,85 sin metadatos. */
    private static byte[] aJpeg(BufferedImage imagen) {
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ImageOutputStream flujo = ImageIO.createImageOutputStream(salida)) {
            ImageWriteParam parametros = escritor.getDefaultWriteParam();
            parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parametros.setCompressionQuality(CALIDAD_JPEG);
            escritor.setOutput(flujo);
            escritor.write(null, new IIOImage(imagen, null, null), parametros);
        } catch (IOException e) {
            throw new UncheckedIOException("No se ha podido codificar la imagen en JPEG", e);
        } finally {
            escritor.dispose();
        }
        return salida.toByteArray();
    }
}
