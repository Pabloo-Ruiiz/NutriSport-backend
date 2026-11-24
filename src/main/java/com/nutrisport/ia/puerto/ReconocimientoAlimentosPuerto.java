package com.nutrisport.ia.puerto;

import java.util.List;

/**
 * Reconocimiento de alimentos en una foto (§4.3, {@code FoodRecognitionPort} en la propuesta). Tiene un
 * adaptador real (Spring AI, LLM multimodal con salida estructurada) y uno simulado.
 */
public interface ReconocimientoAlimentosPuerto {

    /** Versión del prompt, que se guarda en el análisis para poder reproducir el resultado. */
    String VERSION_PROMPT = "reconocimiento-v1";

    /**
     * @param jpeg     foto ya procesada (JPEG ≤ 1024 px y sin EXIF, ProcesadorImagenes)
     * @param contexto tipo de comida y descripción opcional del usuario
     * @return hasta 8 alimentos visibles; lista vacía si no se detecta comida
     * @throws com.nutrisport.comun.excepcion.ServicioNoDisponibleException si el proveedor no responde
     */
    ResultadoIa<List<AlimentoDetectado>> reconocer(byte[] jpeg, ContextoReconocimiento contexto);
}
