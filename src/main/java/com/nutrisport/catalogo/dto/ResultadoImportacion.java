package com.nutrisport.catalogo.dto;

import com.nutrisport.comun.dto.ErrorCampo;
import java.util.List;

/**
 * Resultado de importar alimentos desde CSV.
 *
 * @param creados     alimentos nuevos
 * @param actualizados alimentos que ya existían (mismo nombre, marca y preparación) y se han sobrescrito
 * @param errores     filas no importadas: {@code campo} = "fila N" (N es la línea del fichero, la
 *                    cabecera es la 1) y {@code mensaje} = los motivos separados por "; "
 */
public record ResultadoImportacion(int creados, int actualizados, List<ErrorCampo> errores) {

    public ResultadoImportacion {
        errores = List.copyOf(errores);
    }
}
