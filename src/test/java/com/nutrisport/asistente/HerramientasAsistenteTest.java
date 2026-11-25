package com.nutrisport.asistente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nutrisport.IntegracionBase;
import com.nutrisport.asistente.herramientas.HerramientasAsistente;
import com.nutrisport.asistente.herramientas.HerramientasAsistente.Comidas;
import com.nutrisport.asistente.herramientas.HerramientasAsistente.ResumenDia;
import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.auth.seguridad.UsuarioAutenticado;
import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.FuenteDato;
import com.nutrisport.catalogo.entidad.UnidadBase;
import com.nutrisport.catalogo.entidad.Visibilidad;
import com.nutrisport.catalogo.repositorio.AlimentoRepositorio;
import com.nutrisport.catalogo.repositorio.CategoriaRepositorio;
import com.nutrisport.seguimiento.dto.ResumenDiarioResponse;
import com.nutrisport.seguimiento.servicio.ResumenDiarioServicio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Herramientas de solo lectura del asistente (§4.4 y §8.2) contra PostgreSQL real: el usuario sale solo del
 * {@link ToolContext}, nunca de un parámetro que pueda rellenar el modelo, y las cifras coinciden con las de los
 * servicios de la aplicación.
 * <p>
 * Ayer, Ana comió 200 g de pechuga de pollo (46,6 g de proteína) y Luis, 500 g (116,5 g). Los dos tienen perfil.
 */
@AutoConfigureMockMvc
class HerramientasAsistenteTest extends IntegracionBase {

    private static final LocalDate AYER = LocalDate.now(ZoneId.of("Europe/Madrid")).minusDays(1);
    private static final UUID CARNES = UUID.fromString("c0000000-0000-0000-0000-000000000003");
    private static final String REFERENCIA_ALIMENTO = "TEST-HERRAMIENTAS-POLLO";

    @Autowired
    private HerramientasAsistente herramientas;
    @Autowired
    private ResumenDiarioServicio resumenDiarioServicio;
    @Autowired
    private AlimentoRepositorio alimentoRepositorio;
    @Autowired
    private CategoriaRepositorio categoriaRepositorio;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;

    private UsuarioConToken ana;
    private UsuarioConToken luis;

    @BeforeEach
    void preparar() throws Exception {
        limpiar();
        ana = crearUsuarioYToken();
        luis = crearUsuarioYToken();
        UUID pollo = alimento().getId();
        for (UsuarioConToken usuario : List.of(ana, luis)) {
            crearPerfil(usuario);
        }
        comida(ana, pollo, 200);
        comida(luis, pollo, 500);
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
        jdbc.execute("TRUNCATE registro_comida, usuario CASCADE");
        jdbc.update("DELETE FROM alimento WHERE referencia_externa = ?", REFERENCIA_ALIMENTO);
    }

    @Test
    void resumenDiarioDevuelveSoloLosDatosDelUsuarioDelContextoYCoincideConElResumen() {
        List<String> usadasAna = new CopyOnWriteArrayList<>();
        ResumenDia deAna = herramientas.resumenDiario(AYER, contexto(ana, usadasAna));
        List<String> usadasLuis = new CopyOnWriteArrayList<>();
        ResumenDia deLuis = herramientas.resumenDiario(AYER, contexto(luis, usadasLuis));

        // Cada uno ve lo suyo
        assertThat(deAna.consumido().proteina()).isEqualByComparingTo("46.6");
        assertThat(deAna.comidasRegistradas()).isEqualTo(1);
        assertThat(deLuis.consumido().proteina()).isEqualByComparingTo("116.5");
        assertThat(deLuis.comidasRegistradas()).isEqualTo(1);

        // La misma cifra que el resumen diario de cada usuario (HU-16)
        assertThat(deAna.consumido().proteina()).isEqualByComparingTo(resumenComo(ana).consumido().proteina());
        assertThat(deAna.objetivo().proteina()).isEqualByComparingTo(resumenComo(ana).objetivo().proteina());
        assertThat(deLuis.consumido().proteina()).isEqualByComparingTo(resumenComo(luis).consumido().proteina());

        // Cada contexto anota sus herramientas
        assertThat(usadasAna).containsExactly("resumenDiario");
        assertThat(usadasLuis).containsExactly("resumenDiario");
        herramientas.comidas(AYER.minusDays(1), AYER, contexto(ana, usadasAna));
        assertThat(usadasAna).containsExactly("resumenDiario", "comidas");
    }

    @Test
    void elModeloNoPuedeElegirElUsuarioAunqueLoPidaEnLosArgumentos() throws Exception {
        ToolCallback resumen = callback("resumenDiario");
        // El esquema que ve el modelo no tiene ningún parámetro de usuario
        assertThat(resumen.getToolDefinition().inputSchema()).doesNotContain("usuario");

        // Aunque los argumentos traigan el id de Luis, responde con los datos del contexto (Ana), y sin contexto de
        // seguridad ni en el hilo de la petición
        List<String> usadas = new CopyOnWriteArrayList<>();
        String json = CompletableFuture.supplyAsync(() -> resumen.call(
                "{\"fecha\": \"" + AYER + "\", \"usuarioId\": \"" + luis.usuario().getId() + "\"}",
                contexto(ana, usadas))).get();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(JsonPath.<Double>read(json, "$.consumido.proteina")).isEqualTo(46.6);
        assertThat(JsonPath.<Integer>read(json, "$.comidasRegistradas")).isEqualTo(1);
        assertThat(usadas).containsExactly("resumenDiario");
    }

    @Test
    void sinUsuarioEnElContextoNoHayDatos() {
        assertThatThrownBy(() -> herramientas.resumenDiario(AYER, new ToolContext(Map.of())))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> herramientas.resumenDiario(AYER, null))
                .isInstanceOf(IllegalStateException.class);
        // Por la vía de Spring AI: un contexto vacío lo rechaza el propio Spring AI, y uno sin el usuario, la
        // herramienta (el error se devuelve al modelo como fallo de la herramienta)
        assertThatThrownBy(() -> callback("tendencias").call("{}", new ToolContext(Map.of())))
                .hasMessageContaining("ToolContext is required");
        assertThatThrownBy(() -> callback("tendencias").call("{}", new ToolContext(Map.of("otro", "dato"))))
                .hasMessageContaining("sin el usuario");
    }

    @Test
    void lasComidasSonDelUsuarioYElRangoEstaLimitadoA14Dias() {
        Comidas comidas = herramientas.comidas(AYER.minusDays(6), AYER, contexto(luis, new CopyOnWriteArrayList<>()));
        assertThat(comidas.dias()).hasSize(1);
        assertThat(comidas.dias().get(0).comidas().get(0).alimentos()).containsExactly("Pechuga de pollo (500 g)");
        assertThat(comidas.dias().get(0).totales().proteina()).isEqualByComparingTo("116.5");

        assertThatThrownBy(() -> herramientas.comidas(AYER.minusDays(14), AYER,
                contexto(luis, new CopyOnWriteArrayList<>())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("14 días");
        // 14 días justos sí
        assertThat(herramientas.comidas(AYER.minusDays(13), AYER, contexto(luis, new CopyOnWriteArrayList<>()))
                .dias()).hasSize(1);
    }

    @Test
    void lasDemasHerramientasRespondenSinDatosDeOtros() {
        List<String> usadas = new CopyOnWriteArrayList<>();
        ToolContext deAna = contexto(ana, usadas);

        assertThat(herramientas.entrenamientos(AYER, AYER, deAna).sesiones()).isEmpty();
        assertThat(herramientas.menuDelDia(AYER, deAna).platos()).isEmpty();
        assertThat(herramientas.tendencias(deAna).tendencias()).isEmpty();
        assertThat(usadas).containsExactly("entrenamientos", "menuDelDia", "tendencias");
        // Las cinco herramientas están registradas para Spring AI
        assertThat(Arrays.stream(ToolCallbacks.from(herramientas)).map(c -> c.getToolDefinition().name()))
                .containsExactlyInAnyOrder("resumenDiario", "comidas", "entrenamientos", "menuDelDia", "tendencias");
    }

    // ---------------------------------------------------------------------------------------------

    private static ToolContext contexto(UsuarioConToken usuario, List<String> usadas) {
        return new ToolContext(Map.of(HerramientasAsistente.CLAVE_USUARIO, usuario.usuario().getId(),
                HerramientasAsistente.CLAVE_HERRAMIENTAS, usadas));
    }

    private ToolCallback callback(String nombre) {
        return Arrays.stream(ToolCallbacks.from(herramientas))
                .filter(callback -> callback.getToolDefinition().name().equals(nombre))
                .findFirst()
                .orElseThrow();
    }

    /** El resumen diario de ayer tal como lo vería el propio usuario. */
    private ResumenDiarioResponse resumenComo(UsuarioConToken usuario) {
        Usuario entidad = usuario.usuario();
        UsuarioAutenticado autenticado = UsuarioAutenticado.de(entidad);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(autenticado, null, autenticado.getAuthorities()));
        try {
            return resumenDiarioServicio.de(AYER);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void crearPerfil(UsuarioConToken usuario) throws Exception {
        mockMvc.perform(put("/api/v1/perfil").header(HttpHeaders.AUTHORIZATION, usuario.cabeceraAutorizacion())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"fechaNacimiento": "1995-04-12", "sexo": "MUJER", "alturaCm": 168, "pesoKg": 60.5,
                         "objetivo": "RENDIMIENTO", "deportePrincipal": "CARRERA_12KMH", "nivelActividad": "MODERADO",
                         "diasEntrenamientoSemana": 4, "duracionMediaMin": 60, "comidasPorDia": 4}"""))
                .andExpect(status().isOk());
    }

    private void comida(UsuarioConToken usuario, UUID alimento, int gramos) throws Exception {
        mockMvc.perform(post("/api/v1/comidas").header(HttpHeaders.AUTHORIZATION, usuario.cabeceraAutorizacion())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"fecha": "%s", "hora": "14:00", "tipoComida": "COMIDA",
                         "alimentos": [{"alimentoId": "%s", "cantidad": %d, "unidad": "g"}]}"""
                        .formatted(AYER, alimento, gramos)))
                .andExpect(status().isCreated());
    }

    /** 23,3 g de proteína por 100 g. */
    private Alimento alimento() {
        Alimento alimento = new Alimento();
        alimento.setNombre("Pechuga de pollo");
        alimento.setCategoria(categoriaRepositorio.getReferenceById(CARNES));
        alimento.setKcal100(new BigDecimal("110"));
        alimento.setProteinas100(new BigDecimal("23.3"));
        alimento.setCarbohidratos100(BigDecimal.ZERO);
        alimento.setGrasas100(new BigDecimal("1.5"));
        alimento.setUnidadBase(UnidadBase.G);
        alimento.setVisibilidad(Visibilidad.PUBLICO);
        alimento.setFuente(FuenteDato.BEDCA);
        alimento.setAlergenosRevisados(true);
        alimento.setReferenciaExterna(REFERENCIA_ALIMENTO);
        return alimentoRepositorio.saveAndFlush(alimento);
    }
}
