package com.nutrisport.comun.excepcion;

import com.nutrisport.comun.dto.ErrorCampo;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Construye los Problem Details (RFC 9457) con el formato único del proyecto (§5.4 y §7.12):
 * {@code type} = {@code urn:nutrisport:error:<codigo>}, {@code title} en castellano, {@code status},
 * {@code detail}, {@code instance} = ruta de la petición, y las propiedades {@code codigo} y {@code errors}.
 * <p>
 * La usan {@link ManejadorGlobalExcepciones} y los componentes de seguridad que responden fuera de
 * Spring MVC (401 y 403 de los filtros), para que todas las respuestas de error sean iguales.
 */
public final class FabricaProblemas {

    private static final String PREFIJO_TIPO = "urn:nutrisport:error:";

    private FabricaProblemas() {
    }

    public static ProblemDetail crear(HttpStatusCode estado, String codigo, String detalle,
            List<ErrorCampo> errores, String ruta) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setType(URI.create(PREFIJO_TIPO + codigo.toLowerCase(Locale.ROOT)));
        problema.setTitle(titulo(codigo));
        problema.setInstance(aUri(ruta));
        problema.setProperty("codigo", codigo);
        problema.setProperty("errors", errores == null ? List.of() : errores);
        return problema;
    }

    /** Título corto en castellano para cada código del catálogo (§7.12). */
    public static String titulo(String codigo) {
        return switch (codigo) {
            case "VALIDACION" -> "Datos no válidos";
            case "TOKEN_NO_VALIDO" -> "Token no válido";
            case "NO_AUTENTICADO" -> "No autenticado";
            case "SIN_PERMISO" -> "Acceso denegado";
            case "EMAIL_NO_VERIFICADO" -> "Email no verificado";
            case "CONSENTIMIENTO_REVOCADO" -> "Consentimiento revocado";
            case "NO_ENCONTRADO" -> "Recurso no encontrado";
            case "CONFLICTO" -> "Conflicto";
            case "ARCHIVO_DEMASIADO_GRANDE" -> "Archivo demasiado grande";
            case "ARCHIVO_NO_VALIDO" -> "Archivo no válido";
            case "PERFIL_REQUERIDO" -> "Perfil requerido";
            case "REGLA_NEGOCIO" -> "Regla de negocio incumplida";
            case "NUTRICION_INCOMPLETA" -> "Datos nutricionales incompletos";
            case "LIMITE_EXCEDIDO" -> "Límite excedido";
            case "SERVICIO_NO_DISPONIBLE" -> "Servicio no disponible";
            case "PROVEEDOR_NO_DISPONIBLE" -> "Proveedor externo no disponible";
            case "ERROR_INTERNO" -> "Error interno";
            default -> "Error en la petición";
        };
    }

    /** La ruta llega ya codificada; si aun así no es una URI válida, se omite {@code instance}. */
    private static URI aUri(String ruta) {
        if (ruta == null) {
            return null;
        }
        try {
            return URI.create(ruta);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
