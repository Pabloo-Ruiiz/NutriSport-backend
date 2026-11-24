package com.nutrisport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Comprueba que la aplicación arranca contra PostgreSQL y que el endpoint de salud responde.
 */
@AutoConfigureMockMvc
class ArranqueAplicacionTest extends IntegracionBase {

    private final MockMvc mockMvc;

    @Autowired
    ArranqueAplicacionTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void elContextoArranca() {
        // Si el contexto no arrancara, este test fallaría antes de ejecutarse.
    }

    @Test
    void healthDevuelveUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
