package com.nutrisport.comun.documentacion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nutrisport.IntegracionBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Fuera del perfil local, OpenAPI y Swagger UI están desactivados (application.properties): las rutas de
 * documentación no exponen nada aunque la cadena de seguridad las deje pasar sin token.
 */
@AutoConfigureMockMvc
class DocumentacionDesactivadaTest extends IntegracionBase {

    private final MockMvc mockMvc;

    @Autowired
    DocumentacionDesactivadaTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void apiDocsYSwaggerUiNoEstanDisponibles() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
    }
}
