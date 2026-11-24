package com.nutrisport.comun.documentacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nutrisport.IntegracionBase;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Vigila que la documentación OpenAPI (§5.3) siga completa al añadir o cambiar endpoints: grupos de la
 * lista acordada, resumen y descripción en castellano, seguridad bearerAuth salvo en las rutas públicas,
 * errores como referencias a las respuestas Problem Details y ejemplos en los cuerpos principales.
 * <p>
 * En los tests springdoc está desactivado como en producción; aquí se activa solo para generar el documento.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "springdoc.api-docs.enabled=true")
class DocumentacionOpenApiTest extends IntegracionBase {

    /** Grupos del MVP (issue de documentación); la Ampliación añadirá Productos, Recetas, Menús, Compra y Asistente. */
    private static final Set<String> GRUPOS = Set.of("Auth", "Cuenta", "Perfil", "Catálogo", "Comidas", "Análisis",
            "Actividad", "Seguimiento", "Admin");

    /** Rutas de autenticación que no piden JWT (todas las de /auth salvo /logout-todas). */
    private static final Set<String> RUTAS_PUBLICAS = Set.of("/api/v1/auth/registro", "/api/v1/auth/verificar-email",
            "/api/v1/auth/reenviar-verificacion", "/api/v1/auth/login", "/api/v1/auth/refresh",
            "/api/v1/auth/logout", "/api/v1/auth/recuperar-password", "/api/v1/auth/restablecer-password");

    private static final Set<String> METODOS = Set.of("get", "post", "put", "patch", "delete");

    private final MockMvc mockMvc;
    private String documento;

    @Autowired
    DocumentacionOpenApiTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @BeforeEach
    void generarDocumento() throws Exception {
        documento = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void cadaOperacionTieneUnGrupoDeLaListaYTodosLosGruposExisten() {
        Set<String> usados = new TreeSet<>();
        for (Operacion operacion : operaciones()) {
            List<String> tags = operacion.lista("tags");
            assertThat(tags).as(operacion.nombre()).hasSize(1);
            assertThat(GRUPOS).as(operacion.nombre()).contains(tags.get(0));
            usados.add(tags.get(0));
        }
        assertThat(usados).containsExactlyInAnyOrderElementsOf(GRUPOS);

        List<Map<String, Object>> tagsDocumentados = JsonPath.read(documento, "$.tags");
        assertThat(tagsDocumentados).allSatisfy(tag ->
                assertThat((String) tag.get("description")).as("descripción del grupo %s", tag.get("name")).isNotBlank());
    }

    @Test
    void cadaOperacionTieneResumenYDescripcion() {
        for (Operacion operacion : operaciones()) {
            assertThat((String) operacion.datos().get("summary")).as(operacion.nombre()).isNotBlank();
            assertThat((String) operacion.datos().get("description")).as(operacion.nombre()).isNotBlank();
        }
    }

    @Test
    void soloLasRutasPublicasDeAuthNoPidenToken() {
        for (Operacion operacion : operaciones()) {
            List<Map<String, Object>> seguridad = operacion.lista("security");
            if (RUTAS_PUBLICAS.contains(operacion.ruta())) {
                assertThat(seguridad).as(operacion.nombre()).isNullOrEmpty();
            } else {
                assertThat(seguridad).as(operacion.nombre()).containsExactly(Map.of(RespuestasApi.SEGURIDAD, List.of()));
                assertThat(operacion.respuestas()).as(operacion.nombre()).containsKey("401");
            }
        }
        // Sin requisito global: lo decide cada operación
        Map<String, Object> raiz = JsonPath.read(documento, "$");
        assertThat(raiz).doesNotContainKey("security");
    }

    @Test
    void lasRespuestasDeErrorSonProblemDetails() {
        Map<String, Map<String, Object>> componentes = JsonPath.read(documento, "$.components.responses");
        for (Operacion operacion : operaciones()) {
            operacion.respuestas().forEach((codigo, respuesta) -> {
                if (Integer.parseInt(codigo) >= 400) {
                    String referencia = (String) respuesta.get("$ref");
                    assertThat(referencia).as("%s %s", operacion.nombre(), codigo)
                            .startsWith("#/components/responses/");
                    assertThat(componentes).containsKey(referencia.substring("#/components/responses/".length()));
                }
            });
            // Las operaciones que responden 201, 202 o 204 no documentan además un 200 genérico
            Set<String> exitos = new TreeSet<>(operacion.respuestas().keySet());
            exitos.removeIf(codigo -> !codigo.startsWith("2"));
            assertThat(exitos).as(operacion.nombre()).hasSize(1);
        }

        assertThat(componentes.keySet()).containsExactlyInAnyOrder("Validacion", "NoAutenticado", "SinPermiso",
                "NoEncontrado", "Conflicto", "ArchivoDemasiadoGrande", "TipoNoAdmitido", "ReglaNegocio",
                "LimiteExcedido", "ServicioNoDisponible");
        componentes.forEach((nombre, componente) -> {
            String esquema = JsonPath.read(componente, "$.content['application/problem+json'].schema.$ref");
            assertThat(esquema).as(nombre).isEqualTo("#/components/schemas/ProblemDetail");
        });
        assertThat((Object) JsonPath.read(documento, "$.components.responses.LimiteExcedido.headers['Retry-After']"))
                .isNotNull();
        List<String> obligatorios = JsonPath.read(documento, "$.components.schemas.ProblemDetail.required");
        assertThat(obligatorios).contains("codigo", "errors");
    }

    @Test
    void losCuerposPrincipalesTienenEjemplo() {
        for (String ruta : List.of(
                "$.paths['/api/v1/auth/registro'].post",
                "$.paths['/api/v1/auth/login'].post",
                "$.paths['/api/v1/comidas'].post",
                "$.paths['/api/v1/comidas/{id}'].put",
                "$.paths['/api/v1/entrenamientos'].post",
                "$.paths['/api/v1/entrenamientos/{id}'].put",
                "$.paths['/api/v1/analisis/{id}/confirmar'].post")) {
            Map<String, Object> ejemplos = JsonPath.read(documento, ruta + ".requestBody.content['application/json'].examples");
            assertThat(ejemplos).as(ruta).isNotEmpty();
            String esquema = JsonPath.read(documento, ruta + ".requestBody.content['application/json'].schema.$ref");
            assertThat(esquema).as(ruta).startsWith("#/components/schemas/");
        }
    }

    @Test
    void losFiltrosEstanDocumentados() {
        for (Operacion operacion : operaciones()) {
            List<Map<String, Object>> parametros = operacion.lista("parameters");
            if (parametros == null) {
                continue;
            }
            parametros.stream()
                    .filter(parametro -> "query".equals(parametro.get("in")))
                    // page, size y sort los documenta springdoc a partir de Pageable
                    .filter(parametro -> !Set.of("page", "size", "sort").contains(parametro.get("name")))
                    .forEach(parametro -> assertThat((String) parametro.get("description"))
                            .as("%s ?%s", operacion.nombre(), parametro.get("name")).isNotBlank());
        }
    }

    // ---------------------------------------------------------------------------------------------

    private List<Operacion> operaciones() {
        Map<String, Map<String, Map<String, Object>>> rutas = JsonPath.read(documento, "$.paths");
        List<Operacion> operaciones = new ArrayList<>();
        rutas.forEach((ruta, metodos) -> metodos.forEach((metodo, datos) -> {
            if (METODOS.contains(metodo)) {
                operaciones.add(new Operacion(ruta, metodo, datos));
            }
        }));
        assertThat(operaciones).hasSizeGreaterThan(50);
        return operaciones;
    }

    private record Operacion(String ruta, String metodo, Map<String, Object> datos) {

        String nombre() {
            return metodo.toUpperCase() + " " + ruta;
        }

        @SuppressWarnings("unchecked")
        <T> List<T> lista(String clave) {
            return (List<T>) datos.get(clave);
        }

        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> respuestas() {
            return (Map<String, Map<String, Object>>) datos.get("responses");
        }
    }
}
