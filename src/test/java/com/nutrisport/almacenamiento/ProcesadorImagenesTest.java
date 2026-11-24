package com.nutrisport.almacenamiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nutrisport.comun.excepcion.ArchivoNoValidoException;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Validación y reprocesado de fotos: tamaño, formato JPEG, eliminación de EXIF y rechazo de no imágenes. */
class ProcesadorImagenesTest {

    private final ProcesadorImagenes procesador = new ProcesadorImagenes();

    @Test
    void unPngDe3000x2000SeReduceAUnJpegDe1024x683ConMiniaturaDe320() throws IOException {
        ImagenProcesada resultado = procesador.procesar(png(3000, 2000));

        assertThat(resultado.ancho()).isEqualTo(1024);
        assertThat(resultado.alto()).isEqualTo(683);
        BufferedImage imagen = leer(resultado.imagen());
        assertThat(imagen.getWidth()).isEqualTo(1024);
        assertThat(imagen.getHeight()).isEqualTo(683);
        assertThat(leer(resultado.miniatura()).getWidth()).isEqualTo(320);
    }

    @Test
    void elResultadoEsUnJpegSinSegmentoExif() {
        ImagenProcesada resultado = procesador.procesar(png(3000, 2000));

        for (byte[] jpeg : new byte[][] {resultado.imagen(), resultado.miniatura()}) {
            // FF D8: inicio de un JPEG (SOI)
            assertThat(jpeg[0]).isEqualTo((byte) 0xFF);
            assertThat(jpeg[1]).isEqualTo((byte) 0xD8);
            // FF E1: marcador APP1, donde van EXIF y la geolocalización
            assertThat(contieneSecuencia(jpeg, (byte) 0xFF, (byte) 0xE1)).isFalse();
        }
    }

    @Test
    void unTextoConExtensionJpgSeRechaza() {
        // La extensión no cuenta: el procesador solo recibe bytes y comprueba su firma
        byte[] textoLlamadoFotoJpg = "Esto es un texto guardado como foto.jpg".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> procesador.procesar(textoLlamadoFotoJpg))
                .isInstanceOf(ArchivoNoValidoException.class);
    }

    // ---------------------------------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------------------------------

    /** PNG generado en memoria, con un degradado para que no sea un color plano. */
    private static byte[] png(int ancho, int alto) {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D grafico = imagen.createGraphics();
        try {
            grafico.setPaint(new GradientPaint(0, 0, Color.ORANGE, ancho, alto, Color.GREEN));
            grafico.fillRect(0, 0, ancho, alto);
        } finally {
            grafico.dispose();
        }
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            ImageIO.write(imagen, "png", salida);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return salida.toByteArray();
    }

    private static BufferedImage leer(byte[] jpeg) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(jpeg));
    }

    private static boolean contieneSecuencia(byte[] datos, byte primero, byte segundo) {
        for (int i = 0; i + 1 < datos.length; i++) {
            if (datos[i] == primero && datos[i + 1] == segundo) {
                return true;
            }
        }
        return false;
    }
}
