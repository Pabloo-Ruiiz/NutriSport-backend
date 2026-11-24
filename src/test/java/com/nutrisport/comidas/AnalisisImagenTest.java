package com.nutrisport.comidas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nutrisport.IntegracionBase;
import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.FuenteDato;
import com.nutrisport.catalogo.entidad.Preparacion;
import com.nutrisport.catalogo.entidad.UnidadBase;
import com.nutrisport.catalogo.entidad.Visibilidad;
import com.nutrisport.catalogo.repositorio.AlimentoRepositorio;
import com.nutrisport.catalogo.repositorio.CategoriaRepositorio;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

/**
 * Análisis de fotos (§4.3 y §7.4) con la IA simulada y el procesado síncrono de los tests: la IA
 * simulada detecta siempre salmón a la plancha, arroz blanco cocido, brócoli cocido y aguacate crudo.
 */
@AutoConfigureMockMvc
class AnalisisImagenTest extends IntegracionBase {

    private static final String RUTA = "/api/v1/analisis";
    private static final UUID CATEGORIA_OTROS = UUID.fromString("c0000000-0000-0000-0000-000000000014");

    /** Valores por 100 g de cada alimento del catálogo: kcal, proteína, carbohidratos, grasa. */
    private static final Map<String, List<String>> CATALOGO = Map.of(
            "Salmón", List.of("208", "20.0", "0.0", "13.0"),
            "Arroz blanco cocido", List.of("130", "2.7", "28.0", "0.3"),
            "Brócoli", List.of("35", "2.4", "4.4", "0.4"),
            "Aguacate", List.of("160", "2.0", "1.9", "14.7"));

    private final MockMvc mockMvc;
    private final AlimentoRepositorio alimentoRepositorio;
    private final CategoriaRepositorio categoriaRepositorio;
    private final JdbcTemplate jdbc;

    private UsuarioConToken usuario;

    @Autowired
    AnalisisImagenTest(MockMvc mockMvc, AlimentoRepositorio alimentoRepositorio,
            CategoriaRepositorio categoriaRepositorio, JdbcTemplate jdbc) {
        this.mockMvc = mockMvc;
        this.alimentoRepositorio = alimentoRepositorio;
        this.categoriaRepositorio = categoriaRepositorio;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void preparar() {
        vaciarTablas();
        usuario = crearUsuarioYToken();
        guardarAlimento("Salmón", Preparacion.PLANCHA);
        guardarAlimento("Arroz blanco cocido", Preparacion.COCIDO);
        guardarAlimento("Brócoli", Preparacion.COCIDO);
        guardarAlimento("Aguacate", Preparacion.CRUDO);
    }

    @AfterEach
    void limpiar() {
        vaciarTablas();
    }

    @Test
    void subirUnPngDevuelve202ConLocationYElAnalisisQuedaCompletadoConSusCandidatos() throws Exception {
        String location = subir(usuario, png())
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.tipo").value("COMIDA"))
                .andReturn().getResponse().getHeader("Location");

        String analisis = mockMvc.perform(conToken(get(location), usuario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COMPLETADO"))
                .andExpect(jsonPath("$.candidatos.length()").value(4))
                .andExpect(jsonPath("$.candidatos[*].preparacion")
                        .value(contains("PLANCHA", "COCIDO", "COCIDO", "CRUDO")))
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> candidatos = JsonPath.read(analisis, "$.candidatos");
        for (Map<String, Object> candidato : candidatos) {
            List<Map<String, Object>> coincidencias = lista(candidato.get("coincidencias"));
            assertThat(coincidencias).as("coincidencias de %s", candidato.get("nombreDetectado")).isNotEmpty();
            Map<String, Object> primera = coincidencias.get(0);
            String nombre = (String) primera.get("nombre");
            assertThat(CATALOGO).containsKey(nombre);
            BigDecimal gramos = new BigDecimal(candidato.get("gramosEstimados").toString());
            Map<String, Object> estimados = mapa(primera.get("estimados"));
            List<String> por100 = CATALOGO.get(nombre);
            assertThat(numero(estimados.get("kcal"))).as("kcal de %s", nombre).isCloseTo(esperado(por100.get(0), gramos), within(0.001));
            assertThat(numero(estimados.get("proteina"))).isCloseTo(esperado(por100.get(1), gramos), within(0.001));
            assertThat(numero(estimados.get("carbohidratos"))).isCloseTo(esperado(por100.get(2), gramos), within(0.001));
            assertThat(numero(estimados.get("grasa"))).isCloseTo(esperado(por100.get(3), gramos), within(0.001));
        }
    }

    @Test
    void laUrlDeLaImagenDescargaLaFotoProcesada() throws Exception {
        String id = idDe(subir(usuario, png()));

        String url = JsonPath.read(mockMvc.perform(conToken(get(RUTA + "/{id}/imagen", id), usuario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiraEn").exists())
                .andReturn().getResponse().getContentAsString(), "$.url");

        HttpResponse<byte[]> descarga = HttpClient.newHttpClient()
                .send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(descarga.statusCode()).isEqualTo(200);
        BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(descarga.body()));
        // El PNG de 1600×1200 se guarda como JPEG con el lado largo reducido a 1024
        assertThat(imagen.getWidth()).isEqualTo(1024);
        assertThat(imagen.getHeight()).isEqualTo(768);
    }

    @Test
    void unPdfSeRechazaCon415() throws Exception {
        byte[] pdf = "%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);

        mockMvc.perform(conToken(multipart(RUTA + "/imagenes")
                        .file(new MockMultipartFile("imagen", "menu.pdf", "application/pdf", pdf))
                        .param("tipoComida", "COMIDA"), usuario))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.codigo").value("ARCHIVO_NO_VALIDO"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM analisis_imagen", Integer.class)).isZero();
    }

    @Test
    void unUsuarioSinEmailVerificadoRecibe403EmailNoVerificado() throws Exception {
        jdbc.update("UPDATE usuario SET email_verificado_en = NULL WHERE id = ?", usuario.usuario().getId());

        subir(usuario, png())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("EMAIL_NO_VERIFICADO"));
    }

    @Test
    void elAnalisisDeOtroUsuarioDevuelve404() throws Exception {
        String id = idDe(subir(usuario, png()));
        UsuarioConToken otro = crearUsuarioYToken();

        mockMvc.perform(conToken(get(RUTA + "/{id}", id), otro))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        mockMvc.perform(conToken(get(RUTA + "/{id}/imagen", id), otro))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------------------------------

    private ResultActions subir(UsuarioConToken autor, byte[] imagen) throws Exception {
        return mockMvc.perform(conToken(multipart(RUTA + "/imagenes")
                .file(new MockMultipartFile("imagen", "comida.png", "image/png", imagen))
                .param("tipoComida", "COMIDA")
                .param("descripcion", "salmón con arroz y verduras"), autor));
    }

    private static <T extends AbstractMockHttpServletRequestBuilder<?>> T conToken(T peticion, UsuarioConToken autor) {
        peticion.header("Authorization", autor.cabeceraAutorizacion());
        return peticion;
    }

    private static String idDe(ResultActions resultado) throws Exception {
        return JsonPath.read(resultado.andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString(),
                "$.id");
    }

    /** valor100 × gramos / 100, con un decimal, como CalculadoraNutricional + Nutrientes.redondear(). */
    private static double esperado(String valor100, BigDecimal gramos) {
        return new BigDecimal(valor100).multiply(gramos).movePointLeft(2).setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static double numero(Object valor) {
        return ((Number) valor).doubleValue();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lista(Object valor) {
        return (List<Map<String, Object>>) valor;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapa(Object valor) {
        return (Map<String, Object>) valor;
    }

    /** PNG de 1600×1200 generado en memoria. */
    private static byte[] png() throws IOException {
        BufferedImage imagen = new BufferedImage(1600, 1200, BufferedImage.TYPE_INT_RGB);
        Graphics2D grafico = imagen.createGraphics();
        try {
            grafico.setColor(new Color(230, 120, 60));
            grafico.fillRect(0, 0, 1600, 1200);
        } finally {
            grafico.dispose();
        }
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, "png", salida);
        return salida.toByteArray();
    }

    private void guardarAlimento(String nombre, Preparacion preparacion) {
        List<String> por100 = CATALOGO.get(nombre);
        Alimento alimento = new Alimento();
        alimento.setNombre(nombre);
        alimento.setPreparacion(preparacion);
        alimento.setCategoria(categoriaRepositorio.findById(CATEGORIA_OTROS).orElseThrow());
        alimento.setKcal100(new BigDecimal(por100.get(0)));
        alimento.setProteinas100(new BigDecimal(por100.get(1)));
        alimento.setCarbohidratos100(new BigDecimal(por100.get(2)));
        alimento.setGrasas100(new BigDecimal(por100.get(3)));
        alimento.setUnidadBase(UnidadBase.G);
        alimento.setAlergenosRevisados(true);
        alimento.setFuente(FuenteDato.ADMIN);
        alimento.setReferenciaExterna("DEV");
        alimento.setVisibilidad(Visibilidad.PUBLICO);
        alimentoRepositorio.save(alimento);
    }

    /** Las categorías iniciales (R__categorias.sql) se conservan. */
    private void vaciarTablas() {
        jdbc.execute("TRUNCATE alimento, usuario CASCADE");
    }
}
