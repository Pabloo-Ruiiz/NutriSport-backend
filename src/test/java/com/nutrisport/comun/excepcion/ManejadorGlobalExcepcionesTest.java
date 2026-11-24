package com.nutrisport.comun.excepcion;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nutrisport.comun.dto.ErrorCampo;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Comprueba el formato Problem Details de {@link ManejadorGlobalExcepciones} con un controlador de pruebas.
 * Los filtros de seguridad se desactivan: aquí solo interesa el manejador.
 */
@WebMvcTest(controllers = ManejadorGlobalExcepcionesTest.ControladorPruebas.class)
@AutoConfigureMockMvc(addFilters = false)
// Los errores 500 provocados a propósito no deben volcar su traza en la salida de los tests
@TestPropertySource(properties = "logging.level.com.nutrisport.comun.excepcion.ManejadorGlobalExcepciones=OFF")
@Import(ManejadorGlobalExcepcionesTest.ControladorPruebas.class)
class ManejadorGlobalExcepcionesTest {

    private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

    private final MockMvc mockMvc;

    @Autowired
    ManejadorGlobalExcepcionesTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void recursoNoEncontradoTieneElFormatoCompleto() throws Exception {
        mockMvc.perform(get("/pruebas/no-encontrado"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:nutrisport:error:no_encontrado"))
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("La comida no existe"))
                .andExpect(jsonPath("$.instance").value("/pruebas/no-encontrado"))
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    void peticionInvalidaIncluyeErroresDeCampo() throws Exception {
        mockMvc.perform(get("/pruebas/invalida"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0].campo").value("fecha"))
                .andExpect(jsonPath("$.errors[0].mensaje").value("No puede ser futura"))
                .andExpect(jsonPath("$.errors[1].campo").value("hora"))
                .andExpect(jsonPath("$.errors[1].mensaje").value("Es obligatoria"));
    }

    @Test
    void reglaNegocioConCodigoPropio() throws Exception {
        mockMvc.perform(get("/pruebas/regla"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:nutrisport:error:perfil_requerido"))
                .andExpect(jsonPath("$.title").value("Perfil requerido"))
                .andExpect(jsonPath("$.codigo").value("PERFIL_REQUERIDO"));
    }

    @Test
    void limiteExcedidoAnadeRetryAfter() throws Exception {
        mockMvc.perform(get("/pruebas/limite"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"))
                .andExpect(jsonPath("$.codigo").value("LIMITE_EXCEDIDO"));
    }

    @Test
    void cuerpoConCamposNoValidosDevuelveUnErrorPorCampo() throws Exception {
        mockMvc.perform(post("/pruebas/cuerpo").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\" \",\"gramos\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[?(@.campo == 'nombre')]").exists())
                .andExpect(jsonPath("$.errors[?(@.campo == 'gramos')]").exists());
    }

    @Test
    void parametroSimpleConRestriccionDevuelveValidacion() throws Exception {
        mockMvc.perform(get("/pruebas/parametro").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errors[0].campo").value("size"));
    }

    @Test
    void constraintViolationDevuelveUltimoNodoComoCampo() throws Exception {
        mockMvc.perform(get("/pruebas/constraint"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errors[?(@.campo == 'nombre')]").exists());
    }

    @Test
    void jsonMalFormadoNoMuestraElMensajeDelParser() throws Exception {
        mockMvc.perform(post("/pruebas/cuerpo").contentType(MediaType.APPLICATION_JSON).content("{ roto"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detail", not(containsString("Unexpected"))));
    }

    @Test
    void tipoIncorrectoYParametroQueFaltaDevuelvenValidacion() throws Exception {
        mockMvc.perform(get("/pruebas/numero/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errors[0].campo").value("numero"));
        mockMvc.perform(get("/pruebas/parametro"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errors[0].campo").value("size"));
    }

    @Test
    void archivoDemasiadoGrandeDevuelve413() throws Exception {
        mockMvc.perform(get("/pruebas/archivo-grande"))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.codigo").value("ARCHIVO_DEMASIADO_GRANDE"));
    }

    @Test
    void autenticacionYAccesoDenegado() throws Exception {
        mockMvc.perform(get("/pruebas/no-autenticado"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        mockMvc.perform(get("/pruebas/sin-permiso"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
    }

    @Test
    void integridadDeDatosNoMuestraElSql() throws Exception {
        mockMvc.perform(get("/pruebas/integridad"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO"))
                .andExpect(jsonPath("$.detail", not(containsString("uk_usuario_email"))));
    }

    @Test
    void errorInternoNoMuestraLaCausa() throws Exception {
        mockMvc.perform(get("/pruebas/interno"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                .andExpect(jsonPath("$.detail").value("Error interno del servidor"))
                // Ni la traza ni el mensaje de la excepción llegan al cliente
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(content().string(not(containsString("fallo inesperado con datos internos"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("at com.nutrisport"))));
    }

    @Test
    void rutaInexistenteYMetodoNoAdmitidoLlevanCodigo() throws Exception {
        mockMvc.perform(get("/pruebas/no-existe-esta-ruta"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        mockMvc.perform(post("/pruebas/interno"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    record CuerpoPrueba(@NotBlank String nombre, @Positive BigDecimal gramos) {
    }

    @RestController
    @RequestMapping("/pruebas")
    static class ControladorPruebas {

        private final Validator validador;

        ControladorPruebas(Validator validador) {
            this.validador = validador;
        }

        @GetMapping("/no-encontrado")
        void noEncontrado() {
            throw new RecursoNoEncontradoException("La comida no existe");
        }

        @GetMapping("/invalida")
        void invalida() {
            throw new PeticionInvalidaException("Fecha no válida",
                    List.of(new ErrorCampo("fecha", "No puede ser futura"),
                            new ErrorCampo("hora", "Es obligatoria")));
        }

        @GetMapping("/regla")
        void regla() {
            throw new ReglaNegocioException("PERFIL_REQUERIDO", "Completa tu perfil");
        }

        @GetMapping("/limite")
        void limite() {
            throw new LimiteExcedidoException("Demasiados intentos", 900);
        }

        @PostMapping("/cuerpo")
        void cuerpo(@Valid @RequestBody CuerpoPrueba cuerpo) {
        }

        @GetMapping("/parametro")
        void parametro(@RequestParam @Max(100) int size) {
        }

        @GetMapping("/constraint")
        void constraint() {
            var violaciones = validador.validate(new CuerpoPrueba("", BigDecimal.ONE));
            throw new ConstraintViolationException(violaciones);
        }

        @GetMapping("/numero/{numero}")
        void numero(@PathVariable int numero) {
        }

        @GetMapping("/archivo-grande")
        void archivoGrande() {
            throw new MaxUploadSizeExceededException(10L * 1024 * 1024);
        }

        @GetMapping("/no-autenticado")
        void noAutenticado() {
            throw new BadCredentialsException("credenciales");
        }

        @GetMapping("/sin-permiso")
        void sinPermiso() {
            throw new AuthorizationDeniedException("denegado");
        }

        @GetMapping("/integridad")
        void integridad() {
            throw new DataIntegrityViolationException(
                    "duplicate key value violates unique constraint \"uk_usuario_email\"");
        }

        @GetMapping("/interno")
        void interno() {
            throw new IllegalStateException("fallo inesperado con datos internos");
        }
    }
}
