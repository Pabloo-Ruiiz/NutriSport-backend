package com.nutrisport.comun.excepcion;

import com.nutrisport.comun.dto.ErrorCampo;
import java.util.List;
import org.springframework.http.HttpStatus;

/** Datos de entrada no válidos detectados en el servicio: 400 {@code VALIDACION}. */
public class PeticionInvalidaException extends ApiException {

    public PeticionInvalidaException(String mensaje) {
        super(HttpStatus.BAD_REQUEST, "VALIDACION", mensaje);
    }

    public PeticionInvalidaException(String mensaje, List<ErrorCampo> errores) {
        super(HttpStatus.BAD_REQUEST, "VALIDACION", mensaje, errores);
    }
}
