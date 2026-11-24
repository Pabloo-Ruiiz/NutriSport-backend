package com.nutrisport.comun.documentacion;

/**
 * Constantes de la documentación OpenAPI (§5.3), para usarlas en las anotaciones de springdoc de los
 * controladores.
 * <p>
 * Las respuestas de error están definidas una sola vez en {@code OpenApiConfig} como componentes
 * reutilizables ({@code #/components/responses/...}), todas con el esquema {@code ProblemDetail} y el tipo
 * {@code application/problem+json}. Un controlador las cita así:
 *
 * <pre>{@code @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)}</pre>
 */
public final class RespuestasApi {

    /** Nombre del esquema de seguridad JWT ({@code @SecurityRequirement(name = RespuestasApi.SEGURIDAD)}). */
    public static final String SEGURIDAD = "bearerAuth";

    /** Tipo de contenido de las respuestas de error (RFC 9457). */
    public static final String PROBLEM_JSON = "application/problem+json";

    /** Nombre del esquema de los errores en {@code #/components/schemas}. */
    public static final String ESQUEMA_PROBLEMA = "ProblemDetail";

    private static final String PREFIJO = "#/components/responses/";

    // Nombres de los componentes (sin prefijo), para declararlos en OpenApiConfig
    public static final String VALIDACION_NOMBRE = "Validacion";
    public static final String NO_AUTENTICADO_NOMBRE = "NoAutenticado";
    public static final String SIN_PERMISO_NOMBRE = "SinPermiso";
    public static final String NO_ENCONTRADO_NOMBRE = "NoEncontrado";
    public static final String CONFLICTO_NOMBRE = "Conflicto";
    public static final String ARCHIVO_DEMASIADO_GRANDE_NOMBRE = "ArchivoDemasiadoGrande";
    public static final String TIPO_NO_ADMITIDO_NOMBRE = "TipoNoAdmitido";
    public static final String REGLA_NEGOCIO_NOMBRE = "ReglaNegocio";
    public static final String LIMITE_EXCEDIDO_NOMBRE = "LimiteExcedido";
    public static final String SERVICIO_NO_DISPONIBLE_NOMBRE = "ServicioNoDisponible";

    /** 400: datos no válidos, token de un solo uso no válido o contraseña actual incorrecta. */
    public static final String VALIDACION = PREFIJO + VALIDACION_NOMBRE;
    /** 401: falta el access token, no es válido o ha caducado; credenciales o sesión no válidas. */
    public static final String NO_AUTENTICADO = PREFIJO + NO_AUTENTICADO_NOMBRE;
    /** 403: sin el rol necesario, consentimiento revocado, email sin verificar u origen no permitido. */
    public static final String SIN_PERMISO = PREFIJO + SIN_PERMISO_NOMBRE;
    /** 404: no existe o es de otro usuario. */
    public static final String NO_ENCONTRADO = PREFIJO + NO_ENCONTRADO_NOMBRE;
    /** 409: conflicto con el estado actual o con datos existentes. */
    public static final String CONFLICTO = PREFIJO + CONFLICTO_NOMBRE;
    /** 413: el archivo supera el tamaño máximo. */
    public static final String ARCHIVO_DEMASIADO_GRANDE = PREFIJO + ARCHIVO_DEMASIADO_GRANDE_NOMBRE;
    /** 415: tipo de archivo o de contenido no admitido. */
    public static final String TIPO_NO_ADMITIDO = PREFIJO + TIPO_NO_ADMITIDO_NOMBRE;
    /** 422: regla de negocio incumplida (p. ej. PERFIL_REQUERIDO). */
    public static final String REGLA_NEGOCIO = PREFIJO + REGLA_NEGOCIO_NOMBRE;
    /** 429: límite de peticiones o de uso de IA superado, con cabecera Retry-After. */
    public static final String LIMITE_EXCEDIDO = PREFIJO + LIMITE_EXCEDIDO_NOMBRE;
    /** 503: el proveedor de IA o el almacenamiento no están disponibles. */
    public static final String SERVICIO_NO_DISPONIBLE = PREFIJO + SERVICIO_NO_DISPONIBLE_NOMBRE;

    private RespuestasApi() {
    }
}
