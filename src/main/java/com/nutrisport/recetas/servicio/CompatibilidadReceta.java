package com.nutrisport.recetas.servicio;

import com.nutrisport.perfil.servicio.RestriccionesUsuario;
import com.nutrisport.recetas.entidad.Receta;
import com.nutrisport.recetas.entidad.RecetaIngrediente;

/**
 * Si una receta encaja con las restricciones estrictas de un usuario (§3.D y §7.11, campo {@code compatible}):
 * <ul>
 * <li>Ningún ingrediente emparejado con un alimento del catálogo genera avisos con
 * {@link RestriccionesUsuario#avisosPara} (alérgenos, trazas, lactosa, dietas, rechazos...).</li>
 * <li>Si el usuario tiene alergias estrictas, además los alérgenos de la receta tienen que estar verificados
 * (todos los ingredientes emparejados con alimentos revisados) y ninguno de los derivados en la receta puede
 * ser uno de los suyos.</li>
 * </ul>
 * Un usuario sin restricciones estrictas es compatible con cualquier receta. Los ingredientes sin emparejar no
 * generan avisos de dieta o lactosa: hasta que se emparejen (cálculo nutricional, A3), solo las alergias exigen
 * verificación.
 */
public final class CompatibilidadReceta {

    private CompatibilidadReceta() {
    }

    /** Necesita los ingredientes de la receta (dentro de una transacción si son perezosos). */
    public static boolean compatible(Receta receta, RestriccionesUsuario restricciones) {
        if (sinRestricciones(restricciones)) {
            return true;
        }
        for (RecetaIngrediente ingrediente : receta.getIngredientes()) {
            if (ingrediente.getAlimento() != null && !restricciones.avisosPara(ingrediente.getAlimento()).isEmpty()) {
                return false;
            }
        }
        if (!restricciones.alergenos().isEmpty()) {
            if (!receta.isAlergenosVerificados()) {
                return false;
            }
            boolean contieneAlguno = receta.getAlergenosContiene().stream().anyMatch(restricciones.alergenos()::contains)
                    || receta.getAlergenosTrazas().stream().anyMatch(restricciones.alergenos()::contains);
            if (contieneAlguno) {
                return false;
            }
        }
        return true;
    }

    private static boolean sinRestricciones(RestriccionesUsuario restricciones) {
        return restricciones.alergenos().isEmpty() && !restricciones.intoleranciaLactosa()
                && restricciones.dietas().isEmpty() && restricciones.alimentosRechazados().isEmpty()
                && restricciones.categoriasRechazadas().isEmpty();
    }
}
