package com.nutrisport.comun.config;

import static com.nutrisport.comun.documentacion.RespuestasApi.ARCHIVO_DEMASIADO_GRANDE_NOMBRE;
import static com.nutrisport.comun.documentacion.RespuestasApi.CONFLICTO_NOMBRE;
import static com.nutrisport.comun.documentacion.RespuestasApi.ESQUEMA_PROBLEMA;
import static com.nutrisport.comun.documentacion.RespuestasApi.LIMITE_EXCEDIDO_NOMBRE;
import static com.nutrisport.comun.documentacion.RespuestasApi.NO_AUTENTICADO_NOMBRE;
import static com.nutrisport.comun.documentacion.RespuestasApi.NO_ENCONTRADO_NOMBRE;
import static com.nutrisport.comun.documentacion.RespuestasApi.PROBLEM_JSON;
import static com.nutrisport.comun.documentacion.RespuestasApi.REGLA_NEGOCIO_NOMBRE;
import static com.nutrisport.comun.documentacion.RespuestasApi.SEGURIDAD;
import static com.nutrisport.comun.documentacion.RespuestasApi.SERVICIO_NO_DISPONIBLE_NOMBRE;
import static com.nutrisport.comun.documentacion.RespuestasApi.SIN_PERMISO_NOMBRE;
import static com.nutrisport.comun.documentacion.RespuestasApi.TIPO_NO_ADMITIDO_NOMBRE;
import static com.nutrisport.comun.documentacion.RespuestasApi.VALIDACION_NOMBRE;

import com.nutrisport.comun.excepcion.FabricaProblemas;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de la documentación OpenAPI y de Swagger UI (§5.3). Solo se publica en los perfiles en
 * los que springdoc está activado (local).
 * <p>
 * Declara:
 * <ul>
 * <li>El esquema de seguridad {@code bearerAuth} (JWT en la cabecera Authorization). No se aplica de forma
 * global: cada controlador lo pide con {@code @SecurityRequirement}, y las rutas públicas de
 * autenticación no lo llevan.</li>
 * <li>El esquema {@code ProblemDetail} con el formato real de los errores del proyecto (RFC 9457 más
 * {@code codigo} y {@code errors}, como en {@code FabricaProblemas}).</li>
 * <li>Una respuesta reutilizable por cada código de error, con {@code application/problem+json} y un
 * ejemplo. Los controladores las citan con las constantes de {@code RespuestasApi}.</li>
 * </ul>
 */
@Configuration
public class OpenApiConfig {

    private static final String REF_PROBLEMA = "#/components/schemas/" + ESQUEMA_PROBLEMA;
    private static final String ESQUEMA_ERROR_CAMPO = "ErrorCampo";

    @Bean
    public OpenAPI nutriSportOpenApi() {
        Components componentes = new Components()
                .addSecuritySchemes(SEGURIDAD, new SecurityScheme()
                        .name(SEGURIDAD)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token JWT devuelto por /auth/login o /auth/refresh (dura 15 minutos)."))
                .addSchemas(ESQUEMA_ERROR_CAMPO, esquemaErrorCampo())
                .addSchemas(ESQUEMA_PROBLEMA, esquemaProblema());
        respuestasDeError().forEach(componentes::addResponses);

        return new OpenAPI()
                .info(new Info()
                        .title("NutriSport AI API")
                        .version("v1")
                        .description("API REST de NutriSport AI para que los deportistas registren y analicen "
                                + "su alimentación, sus entrenamientos y su progreso. Los errores siguen el formato "
                                + "Problem Details (RFC 9457) con las propiedades `codigo` y `errors`. Las "
                                + "recomendaciones son orientativas y no sustituyen el consejo de un profesional "
                                + "sanitario o dietista-nutricionista."))
                .components(componentes);
    }

    // ---------------------------------------------------------------------------------------------
    // Esquemas
    // ---------------------------------------------------------------------------------------------

    private static Schema<?> esquemaErrorCampo() {
        return new ObjectSchema()
                .description("Error de un campo de la petición.")
                .addProperty("campo", new StringSchema().description("Nombre del campo").example("pesoKg"))
                .addProperty("mensaje", new StringSchema().description("Qué falla, en castellano")
                        .example("El peso debe estar entre 30 y 250 kg"));
    }

    private static Schema<?> esquemaProblema() {
        return new ObjectSchema()
                .description("Error en formato Problem Details (RFC 9457) con las propiedades del proyecto.")
                .addProperty("type", new StringSchema().format("uri")
                        .description("urn:nutrisport:error:<codigo en minúsculas>")
                        .example("urn:nutrisport:error:validacion"))
                .addProperty("title", new StringSchema().description("Título corto").example("Datos no válidos"))
                .addProperty("status", new IntegerSchema().description("Código HTTP").example(400))
                .addProperty("detail", new StringSchema().description("Explicación para el usuario")
                        .example("Los datos enviados no son válidos"))
                .addProperty("instance", new StringSchema().format("uri").description("Ruta de la petición")
                        .example("/api/v1/seguimiento/peso"))
                .addProperty("codigo", new StringSchema().description("Código de error del catálogo (§7.12)")
                        .example("VALIDACION"))
                .addProperty("errors", new ArraySchema()
                        .items(new Schema<>().$ref("#/components/schemas/" + ESQUEMA_ERROR_CAMPO))
                        .description("Errores por campo; vacío si no aplica"))
                .required(List.of("type", "title", "status", "detail", "codigo", "errors"));
    }

    // ---------------------------------------------------------------------------------------------
    // Respuestas de error reutilizables
    // ---------------------------------------------------------------------------------------------

    private static Map<String, ApiResponse> respuestasDeError() {
        Map<String, ApiResponse> respuestas = new LinkedHashMap<>();
        respuestas.put(VALIDACION_NOMBRE, problema(400, "VALIDACION",
                "Datos no válidos. `errors` indica qué falla en cada campo. Otros códigos: TOKEN_NO_VALIDO (enlace "
                        + "caducado o ya usado) y PASSWORD_INCORRECTA (contraseña actual incorrecta).",
                "Los datos enviados no son válidos"));
        respuestas.put(NO_AUTENTICADO_NOMBRE, problema(401, "NO_AUTENTICADO",
                "Falta el access token, no es válido o ha caducado, o la cuenta ya no está activa. En login y "
                        + "refresh: credenciales o sesión no válidas (mismo mensaje en todos los casos).",
                "Es necesario iniciar sesión"));
        respuestas.put(SIN_PERMISO_NOMBRE, problema(403, "SIN_PERMISO",
                "Sin permiso. Códigos: SIN_PERMISO (falta el rol), CONSENTIMIENTO_REVOCADO (funciones con datos de "
                        + "salud), EMAIL_NO_VERIFICADO (funciones de IA).",
                "No tienes permiso para realizar esta operación"));
        respuestas.put(NO_ENCONTRADO_NOMBRE, problema(404, "NO_ENCONTRADO",
                "No existe o pertenece a otro usuario (no se distingue, para no revelar recursos ajenos).",
                "El recurso solicitado no existe"));
        respuestas.put(CONFLICTO_NOMBRE, problema(409, "CONFLICTO",
                "Conflicto con el estado actual o con datos existentes (p. ej. EMAIL_EN_USO, "
                        + "ANALISIS_NO_CONFIRMABLE o un nombre de categoría repetido).",
                "La operación entra en conflicto con datos existentes"));
        respuestas.put(ARCHIVO_DEMASIADO_GRANDE_NOMBRE, problema(413, "ARCHIVO_DEMASIADO_GRANDE",
                "El archivo supera el tamaño máximo (10 MB).",
                "El archivo supera el tamaño máximo permitido"));
        respuestas.put(TIPO_NO_ADMITIDO_NOMBRE, problema(415, "ARCHIVO_NO_VALIDO",
                "El archivo no es de un tipo admitido o no se puede leer (las fotos se validan por su firma de "
                        + "bytes: solo JPEG o PNG).",
                "La foto debe ser una imagen JPEG o PNG"));
        respuestas.put(REGLA_NEGOCIO_NOMBRE, problema(422, "PERFIL_REQUERIDO",
                "La petición es válida pero incumple una regla de negocio (p. ej. PERFIL_REQUERIDO o REGLA_NEGOCIO).",
                "Completa tu perfil deportivo para calcular tus objetivos"));
        ApiResponse limite = problema(429, "LIMITE_EXCEDIDO",
                "Límite de intentos o de uso de IA superado. Retry-After indica cuántos segundos esperar.",
                "Has superado el número de intentos permitidos. Inténtalo de nuevo más tarde");
        limite.addHeaderObject("Retry-After", new Header()
                .description("Segundos hasta poder reintentar")
                .schema(new IntegerSchema().example(180)));
        respuestas.put(LIMITE_EXCEDIDO_NOMBRE, limite);
        respuestas.put(SERVICIO_NO_DISPONIBLE_NOMBRE, problema(503, "SERVICIO_NO_DISPONIBLE",
                "Un servicio externo no está disponible: SERVICIO_NO_DISPONIBLE (proveedor de IA o almacenamiento de "
                        + "fotos) o PROVEEDOR_NO_DISPONIBLE (API externa de alimentos caída o sin cupo). Retry-After "
                        + "indica cuántos segundos esperar en el segundo caso.",
                "El servicio no está disponible temporalmente"));
        return respuestas;
    }

    private static ApiResponse problema(int estado, String codigo, String descripcion, String detalle) {
        Map<String, Object> ejemplo = new LinkedHashMap<>();
        ejemplo.put("type", "urn:nutrisport:error:" + codigo.toLowerCase(Locale.ROOT));
        ejemplo.put("title", FabricaProblemas.titulo(codigo));
        ejemplo.put("status", estado);
        ejemplo.put("detail", detalle);
        ejemplo.put("instance", "/api/v1/...");
        ejemplo.put("codigo", codigo);
        ejemplo.put("errors", estado == 400
                ? List.of(Map.of("campo", "pesoKg", "mensaje", "El peso debe estar entre 30 y 250 kg"))
                : List.of());
        return new ApiResponse()
                .description(descripcion)
                .content(new Content().addMediaType(PROBLEM_JSON, new MediaType()
                        .schema(new Schema<>().$ref(REF_PROBLEMA))
                        .addExamples(codigo, new Example().value(ejemplo))));
    }
}
