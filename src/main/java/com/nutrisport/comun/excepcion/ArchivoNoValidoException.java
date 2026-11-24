package com.nutrisport.comun.excepcion;

import org.springframework.http.HttpStatus;

/** El archivo subido no es de un tipo admitido (por ejemplo, una foto que no es JPEG ni PNG): 415 {@code ARCHIVO_NO_VALIDO}. */
public class ArchivoNoValidoException extends ApiException {

    public ArchivoNoValidoException(String mensaje) {
        super(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "ARCHIVO_NO_VALIDO", mensaje);
    }
}
