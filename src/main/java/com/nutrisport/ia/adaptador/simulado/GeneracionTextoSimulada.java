package com.nutrisport.ia.adaptador.simulado;

import com.nutrisport.ia.puerto.GeneracionTextoPuerto;
import com.nutrisport.ia.puerto.ResultadoIa;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Generación de texto simulada para desarrollo y tests (§5.7): respuesta fija y determinista, sin
 * llamar a nada externo. Es el adaptador por defecto si no se indica el modo.
 */
@Component
@ConditionalOnProperty(name = "app.ia.modo", havingValue = "simulado", matchIfMissing = true)
public class GeneracionTextoSimulada implements GeneracionTextoPuerto {

    static final String MODELO = "simulado";

    @Override
    public ResultadoIa<String> generar(String plantilla, Map<String, Object> variables) {
        // Variables ordenadas para que la respuesta sea siempre igual con las mismas entradas
        String texto = "[Respuesta simulada de la plantilla '" + plantilla + "' con " + new TreeMap<>(variables) + "]";
        return new ResultadoIa<>(texto, 0, 0, MODELO);
    }
}
