package com.nutrisport.seguridad;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nutrisport.IntegracionBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cabeceras de seguridad y CORS en las respuestas de la API (§8.5), tanto en una respuesta correcta como en
 * un error 401 generado por los filtros de seguridad (fuera de Spring MVC).
 */
@AutoConfigureMockMvc
@Transactional
class CabecerasSeguridadTest extends IntegracionBase {

    private static final String FRONTEND = "http://localhost:4200";
    private static final String ORIGEN_AJENO = "https://atacante.example";
    private static final String CSP_API = "default-src 'none'; frame-ancestors 'none'";

    private final MockMvc mockMvc;

    @Autowired
    CabecerasSeguridadTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void respuestaCorrectaDeLaApiLlevaLasCabecerasDeSeguridad() throws Exception {
        UsuarioConToken usuario = crearUsuarioYToken();

        comprobarCabeceras(mockMvc.perform(get("/api/v1/cuenta")
                        .header("Authorization", usuario.cabeceraAutorizacion()))
                .andExpect(status().isOk()));
    }

    @Test
    void errorDeLosFiltrosDeSeguridadTambienLlevaLasCabeceras() throws Exception {
        comprobarCabeceras(mockMvc.perform(get("/api/v1/cuenta"))
                .andExpect(status().isUnauthorized()));
    }

    @Test
    void preflightDeUnOrigenNoPermitidoSeRechaza() throws Exception {
        mockMvc.perform(options("/api/v1/cuenta")
                        .header("Origin", ORIGEN_AJENO)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void peticionRealDesdeUnOrigenNoPermitidoSeRechazaAunqueLleveToken() throws Exception {
        UsuarioConToken usuario = crearUsuarioYToken();

        mockMvc.perform(get("/api/v1/cuenta")
                        .header("Origin", ORIGEN_AJENO)
                        .header("Authorization", usuario.cabeceraAutorizacion()))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void elOrigenDelFrontendSiSePermite() throws Exception {
        UsuarioConToken usuario = crearUsuarioYToken();

        mockMvc.perform(get("/api/v1/cuenta")
                        .header("Origin", FRONTEND)
                        .header("Authorization", usuario.cabeceraAutorizacion()))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    private static void comprobarCabeceras(ResultActions resultado) throws Exception {
        resultado
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy", CSP_API))
                // Las respuestas con datos personales no deben quedar en cachés intermedias
                .andExpect(header().string("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate"));
    }
}
