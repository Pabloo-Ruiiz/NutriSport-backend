package com.nutrisport.seguimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nutrisport.IntegracionBase;
import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.FuenteDato;
import com.nutrisport.catalogo.entidad.UnidadBase;
import com.nutrisport.catalogo.entidad.Visibilidad;
import com.nutrisport.catalogo.repositorio.AlimentoRepositorio;
import com.nutrisport.catalogo.repositorio.CategoriaRepositorio;
import com.nutrisport.perfil.entidad.NivelActividad;
import com.nutrisport.perfil.entidad.ObjetivoDeportivo;
import com.nutrisport.perfil.entidad.PerfilDeportivo;
import com.nutrisport.perfil.entidad.Sexo;
import com.nutrisport.perfil.repositorio.PerfilDeportivoRepositorio;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Resumen diario: consumido frente al objetivo del día (base + entrenamiento). */
@AutoConfigureMockMvc
class ResumenDiarioTest extends IntegracionBase {

    private static final UUID CATEGORIA_CEREALES = UUID.fromString("c0000000-0000-0000-0000-000000000007");

    private final MockMvc mockMvc;
    private final PerfilDeportivoRepositorio perfilRepositorio;
    private final AlimentoRepositorio alimentoRepositorio;
    private final CategoriaRepositorio categoriaRepositorio;
    private final JdbcTemplate jdbc;
    private final Clock reloj;

    private UsuarioConToken deportista;
    private LocalDate hoy;

    @Autowired
    ResumenDiarioTest(MockMvc mockMvc, PerfilDeportivoRepositorio perfilRepositorio,
            AlimentoRepositorio alimentoRepositorio, CategoriaRepositorio categoriaRepositorio, JdbcTemplate jdbc,
            Clock reloj) {
        this.mockMvc = mockMvc;
        this.perfilRepositorio = perfilRepositorio;
        this.alimentoRepositorio = alimentoRepositorio;
        this.categoriaRepositorio = categoriaRepositorio;
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    @BeforeEach
    void preparar() {
        vaciarTablas();
        deportista = crearUsuarioYToken();
        hoy = LocalDate.now(reloj);
        guardarPerfilConObjetivos(deportista.usuario());
    }

    @AfterEach
    void limpiar() {
        vaciarTablas();
    }

    @Test
    void elConsumidoEsLaSumaDeLasComidasYElRestanteElObjetivoConEntrenoMenosLoConsumido() throws Exception {
        Alimento arroz = guardarAlimento("Arroz blanco cocido", "130", "2.7", "28.0", "0.3");
        Alimento pollo = guardarAlimento("Pechuga de pollo a la plancha", "165", "31.0", "0.0", "3.6");
        Alimento yogur = guardarAlimento("Yogur natural", "61", "3.8", "4.6", "3.0");
        // 60 min de carrera a 9,8 MET con 70 kg e intensidad media: 616 kcal netas
        registrarEntrenamiento();
        // 260 + 247,5 + 76,3 kcal · 5,4 + 46,5 + 4,8 P · 56 + 0 + 5,8 HC · 0,6 + 5,4 + 3,8 G
        List<String> comidas = List.of(
                registrarComida("COMIDA", arroz.getId(), 200),
                registrarComida("CENA", pollo.getId(), 150),
                registrarComida("MERIENDA", yogur.getId(), 125));

        String resumen = mockMvc.perform(get("/api/v1/seguimiento/resumen")
                        .header("Authorization", deportista.cabeceraAutorizacion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha").value(hoy.toString()))
                .andExpect(jsonPath("$.comidas.length()").value(3))
                .andExpect(jsonPath("$.entrenamientos.length()").value(1))
                .andExpect(jsonPath("$.kcalEntreno").value(616.0))
                // Objetivo con entreno: 2308 + 616 kcal y 335 + 616 / 4 g de HC
                .andExpect(jsonPath("$.objetivo.kcal").value(2924.0))
                .andExpect(jsonPath("$.objetivo.carbohidratos").value(489.0))
                .andExpect(jsonPath("$.consumido.kcal").value(583.8))
                .andExpect(jsonPath("$.consumido.proteina").value(56.7))
                .andExpect(jsonPath("$.consumido.carbohidratos").value(61.8))
                .andExpect(jsonPath("$.consumido.grasa").value(9.8))
                .andExpect(jsonPath("$.restante.kcal").value(2340.2))
                .andExpect(jsonPath("$.restante.proteina").value(41.3))
                .andExpect(jsonPath("$.restante.carbohidratos").value(427.2))
                .andExpect(jsonPath("$.restante.grasa").value(54.2))
                .andExpect(jsonPath("$.excesoKcal").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        // El consumido coincide con la suma de los totales de las comidas registradas
        double sumaKcal = comidas.stream()
                .mapToDouble(comida -> ((Number) JsonPath.read(comida, "$.totales.kcal")).doubleValue())
                .sum();
        assertThat(((Number) JsonPath.read(resumen, "$.consumido.kcal")).doubleValue()).isEqualTo(sumaKcal, within(0.001));
    }

    // ---------------------------------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------------------------------

    private void registrarEntrenamiento() throws Exception {
        String fechaHora = hoy.atTime(7, 0).atZone(reloj.getZone()).toOffsetDateTime().toString();
        mockMvc.perform(post("/api/v1/entrenamientos")
                        .header("Authorization", deportista.cabeceraAutorizacion())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipoActividadCodigo": "CARRERA_9_7KMH", "fechaHora": "%s", "duracionMin": 60,
                                 "intensidad": "MEDIA", "estado": "PLANIFICADO"}""".formatted(fechaHora)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kcalEstimadas").value(616));
    }

    /** Registra una comida con un alimento y devuelve el JSON de la respuesta. */
    private String registrarComida(String tipoComida, UUID alimentoId, int gramos) throws Exception {
        return mockMvc.perform(post("/api/v1/comidas")
                        .header("Authorization", deportista.cabeceraAutorizacion())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fecha": "%s", "tipoComida": "%s", "alimentos": [
                                    {"alimentoId": "%s", "cantidad": %d, "unidad": "g"}
                                ]}""".formatted(hoy, tipoComida, alimentoId, gramos)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    /** Perfil de 70 kg con objetivos base fijos (2308 kcal · 98 P · 335 HC · 64 G) para no depender de la edad. */
    private void guardarPerfilConObjetivos(Usuario usuario) {
        PerfilDeportivo perfil = new PerfilDeportivo();
        perfil.setUsuario(usuario);
        perfil.setFechaNacimiento(LocalDate.of(1996, 6, 1));
        perfil.setSexo(Sexo.HOMBRE);
        perfil.setAlturaCm(new BigDecimal("175"));
        perfil.setPesoActualKg(new BigDecimal("70"));
        perfil.setObjetivo(ObjetivoDeportivo.MANTENER);
        perfil.setDeportePrincipal("CARRERA_9_7KMH");
        perfil.setNivelActividad(NivelActividad.MODERADO);
        perfil.setDiasEntrenamientoSemana((short) 4);
        perfil.setDuracionMediaMin((short) 60);
        perfil.setComidasPorDia((short) 4);
        perfil.setObjetivoKcal(new BigDecimal("2308"));
        perfil.setObjetivoProteinaG(new BigDecimal("98"));
        perfil.setObjetivoCarbohidratosG(new BigDecimal("335"));
        perfil.setObjetivoGrasaG(new BigDecimal("64"));
        perfilRepositorio.save(perfil);
    }

    private Alimento guardarAlimento(String nombre, String kcal, String proteinas, String carbohidratos,
            String grasas) {
        Alimento alimento = new Alimento();
        alimento.setNombre(nombre);
        alimento.setCategoria(categoriaRepositorio.findById(CATEGORIA_CEREALES).orElseThrow());
        alimento.setKcal100(new BigDecimal(kcal));
        alimento.setProteinas100(new BigDecimal(proteinas));
        alimento.setCarbohidratos100(new BigDecimal(carbohidratos));
        alimento.setGrasas100(new BigDecimal(grasas));
        alimento.setUnidadBase(UnidadBase.G);
        alimento.setAlergenosRevisados(true);
        alimento.setAptoVegetariano(true);
        alimento.setAptoPescetariano(true);
        alimento.setFuente(FuenteDato.ADMIN);
        alimento.setReferenciaExterna("DEV");
        alimento.setVisibilidad(Visibilidad.PUBLICO);
        return alimentoRepositorio.save(alimento);
    }

    private void vaciarTablas() {
        jdbc.execute("TRUNCATE alimento, usuario CASCADE");
    }
}
