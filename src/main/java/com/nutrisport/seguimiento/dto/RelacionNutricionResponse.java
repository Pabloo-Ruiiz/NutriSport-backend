package com.nutrisport.seguimiento.dto;

import com.nutrisport.comun.dto.Nutrientes;
import java.time.LocalDate;

/**
 * Alimentación en días de entrenamiento frente a días de descanso (GET
 * /api/v1/entrenamientos/relacion-nutricion, §3.G). Solo cuentan los días con alguna comida registrada; un día es de
 * entreno si tiene algún entrenamiento planificado o realizado (no omitido).
 *
 * @param mediaEntreno            media diaria consumida en los días de entreno; cero si no hay
 * @param objetivoEntreno         media de sus objetivos guardados; nulo si no hay
 * @param mediaDescanso           media diaria consumida en los días de descanso; cero si no hay
 * @param objetivoDescanso        media de sus objetivos guardados; nulo si no hay
 * @param sesionesConProteinaPost sesiones con al menos 25 g de proteína registrados en las 2 h siguientes al final
 *                                del entreno (solo cuentan las comidas con hora)
 * @param sesiones                entrenamientos planificados o realizados del rango
 */
public record RelacionNutricionResponse(LocalDate desde, LocalDate hasta, Nutrientes mediaEntreno,
        Nutrientes objetivoEntreno, Nutrientes mediaDescanso, Nutrientes objetivoDescanso, int diasEntreno,
        int diasDescanso, int sesionesConProteinaPost, int sesiones) {
}
