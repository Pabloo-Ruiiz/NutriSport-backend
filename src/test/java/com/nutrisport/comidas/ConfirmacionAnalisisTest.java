package com.nutrisport.comidas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nutrisport.IntegracionBase;
import com.nutrisport.almacenamiento.AlmacenamientoServicio;
import com.nutrisport.almacenamiento.ClavesAlmacenamiento;
import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.FuenteDato;
import com.nutrisport.catalogo.entidad.Preparacion;
import com.nutrisport.catalogo.entidad.UnidadBase;
import com.nutrisport.catalogo.entidad.Visibilidad;
import com.nutrisport.catalogo.repositorio.AlimentoRepositorio;
import com.nutrisport.catalogo.repositorio.CategoriaRepositorio;
import com.nutrisport.comidas.servicio.LimpiezaImagenesTarea;
import com.nutrisport.comun.excepcion.ServicioNoDisponibleException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

/** Flujos de confirmación, descarte y retención del análisis de fotos (§4.3, paso 6). */
@AutoConfigureMockMvc
class ConfirmacionAnalisisTest extends IntegracionBase {

    private static final String RUTA = "/api/v1/analisis";
    private static final UUID CATEGORIA_OTROS = UUID.fromString("c0000000-0000-0000-0000-000000000014");

    private static final Map<String, List<String>> CATALOGO = Map.of(
            "Salmón", List.of("208", "20.0", "0.0", "13.0"),
            "Arroz blanco cocido", List.of("130", "2.7", "28.0", "0.3"),
            "Brócoli", List.of("35", "2.4", "4.4", "0.4"),
            "Aguacate", List.of("160", "2.0", "1.9", "14.7"));

    private final MockMvc mockMvc;
    private final AlimentoRepositorio alimentoRepositorio;
    private final CategoriaRepositorio categoriaRepositorio;
    private final JdbcTemplate jdbc;
    private final AlmacenamientoServicio almacenamiento;
    private final LimpiezaImagenesTarea limpiezaImagenes;
    private final Clock reloj;

    private UsuarioConToken usuario;

    @Autowired
    ConfirmacionAnalisisTest(MockMvc mockMvc, AlimentoRepositorio alimentoRepositorio,
            CategoriaRepositorio categoriaRepositorio, JdbcTemplate jdbc, AlmacenamientoServicio almacenamiento,
            LimpiezaImagenesTarea limpiezaImagenes, Clock reloj) {
        this.mockMvc = mockMvc;
        this.alimentoRepositorio = alimentoRepositorio;
        this.categoriaRepositorio = categoriaRepositorio;
        this.jdbc = jdbc;
        this.almacenamiento = almacenamiento;
        this.limpiezaImagenes = limpiezaImagenes;
        this.reloj = reloj;
    }

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE alimento, usuario CASCADE");
        usuario = crearUsuarioYToken();
        guardarAlimentos();
    }

    @AfterEach
    void limpiar() {
        almacenamiento.eliminarPrefijo(ClavesAlmacenamiento.prefijoUsuario(usuario.usuario().getId()));
    }

    @Test
    void confirmarRegistraLaComidaUnaVezYGuardaLasCorrecciones() throws Exception {
        String id = idDe(subir(usuario, png()));
        String resultado = mockMvc.perform(conToken(get(RUTA + "/{id}", id), usuario))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> candidatos = lista(JsonPath.read(resultado, "$.candidatos"));
        Map<String, Object> primero = candidatos.get(0);
        Map<String, Object> segundaCoincidencia =
                lista(candidatos.get(1).get("coincidencias")).get(0);
        String alimentoPrimero = lista(primero.get("coincidencias")).get(0).get("alimentoId").toString();
        String gramosPrimero = primero.get("gramosEstimados").toString();
        String peticion = """
                {"fecha":"%s","hora":"12:30:00","tipoComida":"COMIDA","alimentos":[
                  {"alimentoId":"%s","gramos":%s,"indiceCandidato":0},
                  {"alimentoId":"%s","gramos":50,"indiceCandidato":null}
                ]}
                """.formatted(LocalDate.now(reloj), alimentoPrimero, gramosPrimero,
                segundaCoincidencia.get("alimentoId"));

        mockMvc.perform(conToken(post(RUTA + "/{id}/confirmar", id)
                        .contentType(MediaType.APPLICATION_JSON).content(peticion), usuario))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origen").value("FOTO"))
                .andExpect(jsonPath("$.alimentos[0].origenIa").value(true))
                .andExpect(jsonPath("$.alimentos[0].confianzaIa").value(primero.get("confianza")))
                .andExpect(jsonPath("$.alimentos[1].origenIa").value(false));

        assertThat(jdbc.queryForObject("SELECT estado FROM analisis_imagen WHERE id = ?",
                String.class, UUID.fromString(id))).isEqualTo("CONFIRMADO");
        assertThat(jdbc.queryForObject("SELECT registro_comida_id IS NOT NULL FROM analisis_imagen WHERE id = ?",
                Boolean.class, UUID.fromString(id))).isTrue();
        assertThat(correccion(id, "aceptadosSinCambio")).isEqualTo("1");
        assertThat(correccion(id, "eliminados")).isEqualTo("3");
        assertThat(correccion(id, "anadidos")).isEqualTo("1");

        mockMvc.perform(conToken(post(RUTA + "/{id}/confirmar", id)
                        .contentType(MediaType.APPLICATION_JSON).content(peticion), usuario))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ANALISIS_NO_CONFIRMABLE"));
    }

    @Test
    void descartarUnAnalisisCompletadoDevuelve204() throws Exception {
        String id = idDe(subir(usuario, png()));

        mockMvc.perform(conToken(post(RUTA + "/{id}/descartar", id), usuario))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT estado FROM analisis_imagen WHERE id = ?",
                String.class, UUID.fromString(id))).isEqualTo("DESCARTADO");
    }

    @Test
    void laLimpiezaBorraLaImagenDeUnAnalisisDeHace91Dias() throws Exception {
        String id = idDe(subir(usuario, png()));
        UUID analisisId = UUID.fromString(id);
        String clave = jdbc.queryForObject("SELECT imagen_key FROM analisis_imagen WHERE id = ?",
                String.class, analisisId);
        LocalDate hoy = LocalDate.now(reloj);

        assertThat(almacenamiento.descargar(clave)).isNotEmpty();
        assertThat(limpiezaImagenes.ejecutar(hoy.plusDays(91))).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT imagen_key IS NULL AND miniatura_key IS NULL
                FROM analisis_imagen WHERE id = ?
                """, Boolean.class, analisisId)).isTrue();
        assertThatThrownBy(() -> almacenamiento.descargar(clave))
                .isInstanceOf(ServicioNoDisponibleException.class);
    }

    private String correccion(String id, String propiedad) {
        return jdbc.queryForObject("SELECT correcciones->>? FROM analisis_imagen WHERE id = ?",
                String.class, propiedad, UUID.fromString(id));
    }

    private ResultActions subir(UsuarioConToken autor, byte[] imagen) throws Exception {
        return mockMvc.perform(conToken(multipart(RUTA + "/imagenes")
                .file(new MockMultipartFile("imagen", "comida.png", "image/png", imagen))
                .param("tipoComida", "COMIDA")
                .param("descripcion", "salmón con arroz y verduras"), autor));
    }

    private void guardarAlimentos() {
        guardarAlimento("Salmón", Preparacion.PLANCHA);
        guardarAlimento("Arroz blanco cocido", Preparacion.COCIDO);
        guardarAlimento("Brócoli", Preparacion.COCIDO);
        guardarAlimento("Aguacate", Preparacion.CRUDO);
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

    private static String idDe(ResultActions resultado) throws Exception {
        return JsonPath.read(resultado.andExpect(status().isAccepted()).andReturn()
                .getResponse().getContentAsString(), "$.id");
    }

    private static <T extends AbstractMockHttpServletRequestBuilder<?>> T conToken(T peticion,
            UsuarioConToken autor) {
        peticion.header("Authorization", autor.cabeceraAutorizacion());
        return peticion;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lista(Object valor) {
        return (List<Map<String, Object>>) valor;
    }

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
}
