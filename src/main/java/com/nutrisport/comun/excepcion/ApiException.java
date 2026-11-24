package com.nutrisport.comun.excepcion;

import com.nutrisport.comun.dto.ErrorCampo;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Excepción de negocio que se traduce en una respuesta Problem Details con el estado HTTP,
 * el código de error (§7.12) y, opcionalmente, los errores por campo.
 * La convierte {@link ManejadorGlobalExcepciones}.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus estado;
    private final String codigo;
    private final List<ErrorCampo> errores;

    protected ApiException(HttpStatus estado, String codigo, String mensaje) {
        this(estado, codigo, mensaje, List.of());
    }

    protected ApiException(HttpStatus estado, String codigo, String mensaje, List<ErrorCampo> errores) {
        super(mensaje);
        this.estado = estado;
        this.codigo = codigo;
        this.errores = errores == null ? List.of() : List.copyOf(errores);
    }

    public HttpStatus getEstado() {
        return estado;
    }

    public String getCodigo() {
        return codigo;
    }

    public List<ErrorCampo> getErrores() {
        return errores;
    }
}
