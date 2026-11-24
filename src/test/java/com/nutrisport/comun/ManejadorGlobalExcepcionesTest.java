package com.nutrisport.comun;

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
import com.nutrisport.comun.excepcion.LimiteExcedidoException;
import com.nutrisport.comun.excepcion.PeticionInvalidaException;
import com.nutrisport.comun.excepcion.RecursoNoEncontradoException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Comprueba que {@link com.nutrisport.comun.excepcion.ManejadorGlobalExcepciones} devuelve Problem Details
 * con {@code codigo} y {@code errors}. Los filtros de seguridad se desactivan: solo interesa el manejador.
 */
@WebMvcTest(controllers = ManejadorGlobalExcepcionesTest.ControladorPrueba.class)
@AutoConfigureMockMvc(addFilters = false)
// Los errores 500 provocados a propósito no deben volcar su traza en la salida de los tests
@TestPropertySource(properties = "logging.level.com.nutrisport.comun.excepcion.ManejadorGlobalExcepciones=OFF")
@Import(ManejadorGlobalExcepcionesTest.ControladorPrueba.class)
class ManejadorGlobalExcepcionesTest {

    private static final String MENSAJE_INTERNO = "detalle interno que no debe salir";

    private final MockMvc mockMvc;

    @Autowired
    ManejadorGlobalExcepcionesTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void recursoNoEncontradoDevuelve404() throws Exception {
        mockMvc.perform(get("/prueba-errores/no-encontrado"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.type").value("urn:nutrisport:error:no_encontrado"))
                .andExpect(jsonPath("$.detail").value("El alimento no existe"))
                .andExpect(jsonPath("$.instance").value("/prueba-errores/no-encontrado"))
                .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    void peticionInvalidaDevuelve400ConDosErrores() throws Exception {
        mockMvc.perform(get("/prueba-errores/invalida"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0].campo").value("pesoKg"))
                .andExpect(jsonPath("$.errors[0].mensaje").value("Debe ser positivo"))
                .andExpect(jsonPath("$.errors[1].campo").value("alturaCm"))
                .andExpect(jsonPath("$.errors[1].mensaje").value("Es obligatoria"));
    }

    @Test
    void limiteExcedidoDevuelve429ConRetryAfter() throws Exception {
        mockMvc.perform(get("/prueba-errores/limite"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.codigo").value("LIMITE_EXCEDIDO"))
                .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    void errorInternoDevuelve500SinTraza() throws Exception {
        mockMvc.perform(get("/prueba-errores/interno"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                .andExpect(jsonPath("$.detail").value("Error interno del servidor"))
                .andExpect(jsonPath("$.errors", hasSize(0)))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(content().string(not(containsString(MENSAJE_INTERNO))))
                .andExpect(content().string(not(containsString("RuntimeException"))))
                .andExpect(content().string(not(containsString("at com.nutrisport"))));
    }

    @Test
    void cuerpoNoValidoDevuelve400ConElCampo() throws Exception {
        mockMvc.perform(post("/prueba-errores/cuerpo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].campo").value("nombre"));
    }

    @Test
    void cuerpoValidoDevuelve200() throws Exception {
        mockMvc.perform(post("/prueba-errores/cuerpo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Avena\"}"))
                .andExpect(status().isOk());
    }

    record PeticionPrueba(@NotBlank String nombre) {
    }

    @RestController
    @RequestMapping("/prueba-errores")
    static class ControladorPrueba {

        @GetMapping("/no-encontrado")
        void noEncontrado() {
            throw new RecursoNoEncontradoException("El alimento no existe");
        }

        @GetMapping("/invalida")
        void invalida() {
            throw new PeticionInvalidaException("Datos del perfil no válidos", List.of(
                    new ErrorCampo("pesoKg", "Debe ser positivo"),
                    new ErrorCampo("alturaCm", "Es obligatoria")));
        }

        @GetMapping("/limite")
        void limite() {
            throw new LimiteExcedidoException("Has superado el límite de intentos", 60);
        }

        @GetMapping("/interno")
        void interno() {
            throw new RuntimeException(MENSAJE_INTERNO);
        }

        @PostMapping("/cuerpo")
        void cuerpo(@Valid @RequestBody PeticionPrueba peticion) {
        }
    }
}
