package com.nutrisport.auth.seguridad;

import com.nutrisport.comun.excepcion.FabricaProblemas;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Escribe un Problem Details directamente en la respuesta, para los errores que se producen en los
 * filtros de seguridad, fuera de Spring MVC (y, por tanto, fuera del manejador global).
 * Usa el {@link JsonMapper} de la aplicación con el mismo conversor que Spring MVC, que aplana las
 * propiedades {@code codigo} y {@code errors} igual que en el resto de respuestas.
 */
@Component
class EscritorProblemas {

    private final JacksonJsonHttpMessageConverter conversor;

    EscritorProblemas(JsonMapper jsonMapper) {
        this.conversor = new JacksonJsonHttpMessageConverter(jsonMapper.rebuild());
    }

    void escribir(HttpServletRequest peticion, HttpServletResponse respuesta, HttpStatus estado, String codigo,
            String detalle) throws IOException {
        if (respuesta.isCommitted()) {
            return;
        }
        ProblemDetail problema = FabricaProblemas.crear(estado, codigo, detalle, List.of(), peticion.getRequestURI());
        respuesta.setStatus(estado.value());
        ServletServerHttpResponse salida = new ServletServerHttpResponse(respuesta);
        conversor.write(problema, MediaType.APPLICATION_PROBLEM_JSON, salida);
        salida.flush();
    }
}
