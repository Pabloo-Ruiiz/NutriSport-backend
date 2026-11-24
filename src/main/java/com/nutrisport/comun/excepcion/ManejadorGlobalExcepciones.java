package com.nutrisport.comun.excepcion;

import com.nutrisport.comun.dto.ErrorCampo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Convierte todas las excepciones en respuestas Problem Details (RFC 9457, §5.4 y §7.12) con:
 * {@code type} = {@code urn:nutrisport:error:<codigo>}, {@code title}, {@code status}, {@code detail},
 * {@code instance} = ruta de la petición, y las propiedades {@code codigo} y {@code errors}.
 * <p>
 * Extiende {@link ResponseEntityExceptionHandler} para que el resto de excepciones de Spring MVC
 * (ruta inexistente, método no admitido...) también lleven {@code codigo} en lugar de acabar en 500,
 * y para que Spring Boot no registre su propio manejador de Problem Details.
 */
@RestControllerAdvice
public class ManejadorGlobalExcepciones extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorGlobalExcepciones.class);

    private static final String VALIDACION = "VALIDACION";
    private static final String MENSAJE_VALIDACION = "Los datos enviados no son válidos";

    // ---------------------------------------------------------------------------------------------
    // Excepciones propias
    // ---------------------------------------------------------------------------------------------

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> manejarApiException(ApiException ex, HttpServletRequest peticion) {
        ProblemDetail problema = crearProblema(ex.getEstado(), ex.getCodigo(), ex.getMessage(),
                ex.getErrores(), peticion.getRequestURI());
        ResponseEntity.BodyBuilder respuesta = ResponseEntity.status(ex.getEstado());
        if (ex instanceof LimiteExcedidoException limite) {
            respuesta.header(HttpHeaders.RETRY_AFTER, String.valueOf(limite.getSegundosReintento()));
        }
        return respuesta.body(problema);
    }

    // ---------------------------------------------------------------------------------------------
    // Validación y lectura de la petición (400 VALIDACION)
    // ---------------------------------------------------------------------------------------------

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders cabeceras, HttpStatusCode estado, WebRequest peticion) {
        AgrupadorErrores errores = new AgrupadorErrores();
        ex.getBindingResult().getFieldErrors().forEach(error -> errores.anadir(error.getField(), error));
        ex.getBindingResult().getGlobalErrors().forEach(error -> errores.anadir(error.getObjectName(), error));
        return responder(ex, HttpStatus.BAD_REQUEST, VALIDACION, MENSAJE_VALIDACION, errores.lista(),
                cabeceras, peticion);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders cabeceras, HttpStatusCode estado, WebRequest peticion) {
        AgrupadorErrores errores = new AgrupadorErrores();
        for (ParameterValidationResult resultado : ex.getParameterValidationResults()) {
            if (resultado instanceof ParameterErrors erroresObjeto) {
                // Parámetro de tipo objeto (p. ej. @Valid @RequestBody): errores de sus campos
                for (FieldError error : erroresObjeto.getFieldErrors()) {
                    errores.anadir(error.getField(), error);
                }
                for (ObjectError error : erroresObjeto.getGlobalErrors()) {
                    errores.anadir(error.getObjectName(), error);
                }
            } else {
                // Parámetro simple (@RequestParam, @PathVariable...) con restricciones
                String nombre = resultado.getMethodParameter().getParameterName();
                for (MessageSourceResolvable error : resultado.getResolvableErrors()) {
                    errores.anadir(nombre, error);
                }
            }
        }
        return responder(ex, HttpStatus.BAD_REQUEST, VALIDACION, MENSAJE_VALIDACION, errores.lista(),
                cabeceras, peticion);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> manejarConstraintViolation(ConstraintViolationException ex,
            HttpServletRequest peticion) {
        AgrupadorErrores errores = new AgrupadorErrores();
        for (ConstraintViolation<?> violacion : ex.getConstraintViolations()) {
            errores.anadir(ultimoNodo(violacion.getPropertyPath()), violacion.getMessage());
        }
        return ResponseEntity.badRequest().body(crearProblema(HttpStatus.BAD_REQUEST, VALIDACION,
                MENSAJE_VALIDACION, errores.lista(), peticion.getRequestURI()));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders cabeceras, HttpStatusCode estado, WebRequest peticion) {
        // No se muestra el mensaje del parser: puede revelar detalles internos
        return responder(ex, HttpStatus.BAD_REQUEST, VALIDACION,
                "El cuerpo de la petición falta, está mal formado o contiene campos no admitidos",
                List.of(), cabeceras, peticion);
    }

    /** Incluye {@code MethodArgumentTypeMismatchException} (parámetro con un tipo incorrecto). */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders cabeceras,
            HttpStatusCode estado, WebRequest peticion) {
        String campo = ex.getPropertyName() != null ? ex.getPropertyName() : "parametro";
        List<ErrorCampo> errores = List.of(new ErrorCampo(campo, "Tiene un formato no válido"));
        return responder(ex, HttpStatus.BAD_REQUEST, VALIDACION,
                "El parámetro '" + campo + "' tiene un valor no válido", errores, cabeceras, peticion);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
            HttpHeaders cabeceras, HttpStatusCode estado, WebRequest peticion) {
        List<ErrorCampo> errores = List.of(new ErrorCampo(ex.getParameterName(), "Es obligatorio"));
        return responder(ex, HttpStatus.BAD_REQUEST, VALIDACION,
                "Falta el parámetro obligatorio '" + ex.getParameterName() + "'", errores, cabeceras, peticion);
    }

    // ---------------------------------------------------------------------------------------------
    // Archivos, seguridad y base de datos
    // ---------------------------------------------------------------------------------------------

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
            HttpHeaders cabeceras, HttpStatusCode estado, WebRequest peticion) {
        return responder(ex, HttpStatus.CONTENT_TOO_LARGE, "ARCHIVO_DEMASIADO_GRANDE",
                "El archivo supera el tamaño máximo permitido", List.of(), cabeceras, peticion);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> manejarAutenticacion(AuthenticationException ex,
            HttpServletRequest peticion) {
        // El login lanza BadCredentialsException con un mensaje genérico propio ("Email o contraseña
        // incorrectos"); el resto de excepciones de Spring Security llevan textos internos en inglés
        String detalle = ex instanceof BadCredentialsException ? ex.getMessage() : "Es necesario iniciar sesión";
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(crearProblema(HttpStatus.UNAUTHORIZED,
                "NO_AUTENTICADO", detalle, List.of(), peticion.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> manejarAccesoDenegado(AccessDeniedException ex,
            HttpServletRequest peticion) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(crearProblema(HttpStatus.FORBIDDEN,
                "SIN_PERMISO", "No tienes permiso para realizar esta operación", List.of(),
                peticion.getRequestURI()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> manejarIntegridadDatos(DataIntegrityViolationException ex,
            HttpServletRequest peticion) {
        // El mensaje SQL solo va al log: puede revelar tablas, columnas o datos de otros usuarios
        LOG.warn("Violación de integridad de datos en {}: {}", peticion.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(crearProblema(HttpStatus.CONFLICT, "CONFLICTO",
                "La operación entra en conflicto con datos existentes", List.of(), peticion.getRequestURI()));
    }

    // ---------------------------------------------------------------------------------------------
    // Resto de casos
    // ---------------------------------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> manejarErrorInterno(Exception ex, HttpServletRequest peticion) {
        LOG.error("Error no controlado en {} {}", peticion.getMethod(), peticion.getRequestURI(), ex);
        return ResponseEntity.internalServerError().body(crearProblema(HttpStatus.INTERNAL_SERVER_ERROR,
                "ERROR_INTERNO", "Error interno del servidor", List.of(), peticion.getRequestURI()));
    }

    /**
     * Resto de excepciones de Spring MVC que gestiona la clase base (ruta inexistente, método o tipo
     * de contenido no admitido...): se respeta su estado HTTP y sus cabeceras (p. ej. {@code Allow})
     * y se sustituye el cuerpo por el formato del proyecto.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object cuerpo, HttpHeaders cabeceras,
            HttpStatusCode estado, WebRequest peticion) {
        String codigo;
        String detalle;
        if (estado.is5xxServerError()) {
            LOG.error("Error interno de Spring MVC en {}", ruta(peticion), ex);
            codigo = estado.value() == 503 ? "SERVICIO_NO_DISPONIBLE" : "ERROR_INTERNO";
            detalle = estado.value() == 503 ? "El servicio no está disponible temporalmente"
                    : "Error interno del servidor";
        } else {
            codigo = switch (estado.value()) {
                case 401 -> "NO_AUTENTICADO";
                case 403 -> "SIN_PERMISO";
                case 404 -> "NO_ENCONTRADO";
                default -> VALIDACION;
            };
            detalle = switch (estado.value()) {
                case 404 -> "El recurso solicitado no existe";
                case 405 -> "Método HTTP no admitido en esta ruta";
                case 406 -> "No se puede generar la respuesta en el formato solicitado";
                case 415 -> "Tipo de contenido no admitido";
                default -> "La petición no es válida";
            };
        }
        return responder(ex, estado, codigo, detalle, List.of(), cabeceras, peticion);
    }

    // ---------------------------------------------------------------------------------------------
    // Utilidades
    // ---------------------------------------------------------------------------------------------

    private ResponseEntity<Object> responder(Exception ex, HttpStatusCode estado, String codigo, String detalle,
            List<ErrorCampo> errores, HttpHeaders cabeceras, WebRequest peticion) {
        ProblemDetail problema = crearProblema(estado, codigo, detalle, errores, ruta(peticion));
        // super: conserva las comprobaciones de la clase base (respuesta ya enviada, etc.)
        return super.handleExceptionInternal(ex, problema, cabeceras, estado, peticion);
    }

    private ProblemDetail crearProblema(HttpStatusCode estado, String codigo, String detalle,
            List<ErrorCampo> errores, String ruta) {
        return FabricaProblemas.crear(estado, codigo, detalle, errores, ruta);
    }

    private static String ruta(WebRequest peticion) {
        if (peticion instanceof ServletWebRequest servlet) {
            return servlet.getRequest().getRequestURI();
        }
        return null;
    }

    /** De {@code "registrar.peticion.gramos"} devuelve {@code "gramos"}. */
    private static String ultimoNodo(Path ruta) {
        String nombre = null;
        for (Path.Node nodo : ruta) {
            if (nodo.getName() != null) {
                nombre = nodo.getName();
            }
        }
        return nombre != null ? nombre : ruta.toString();
    }

    /** Agrupa los mensajes por campo para devolver un único {@link ErrorCampo} por campo. */
    private static final class AgrupadorErrores {

        private final Map<String, List<String>> mensajes = new LinkedHashMap<>();

        void anadir(String campo, MessageSourceResolvable error) {
            anadir(campo, error.getDefaultMessage());
        }

        void anadir(String campo, String mensaje) {
            List<String> lista = mensajes.computeIfAbsent(campo, clave -> new ArrayList<>());
            String texto = mensaje != null ? mensaje : "No es válido";
            if (!lista.contains(texto)) {
                lista.add(texto);
            }
        }

        List<ErrorCampo> lista() {
            return mensajes.entrySet().stream()
                    .map(entrada -> new ErrorCampo(entrada.getKey(), String.join("; ", entrada.getValue())))
                    .toList();
        }
    }
}
