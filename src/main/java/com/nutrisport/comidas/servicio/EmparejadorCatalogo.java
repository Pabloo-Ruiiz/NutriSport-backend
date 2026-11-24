package com.nutrisport.comidas.servicio;

import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.Preparacion;
import com.nutrisport.catalogo.repositorio.AlimentoRepositorio;
import com.nutrisport.catalogo.repositorio.AlimentoRepositorio.SimilitudAlimento;
import com.nutrisport.catalogo.servicio.CalculadoraNutricional;
import com.nutrisport.comidas.dto.CandidatoAnalisis;
import com.nutrisport.comidas.dto.CoincidenciaCatalogo;
import com.nutrisport.comun.util.TextoNormalizado;
import com.nutrisport.ia.puerto.AlimentoDetectado;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Empareja cada alimento detectado por la IA con el catálogo (§4.3, paso 3): búsqueda por similitud
 * entre los alimentos visibles para el usuario, priorizando la variante con la misma preparación (por
 * eso el catálogo semilla tiene variantes cocinadas). Los nutrientes estimados se calculan con el
 * catálogo, nunca con la IA (§4.1).
 */
@Component
@Transactional(readOnly = true)
public class EmparejadorCatalogo {

    static final int MAXIMO_COINCIDENCIAS = 3;
    /** Resultados que se piden a la búsqueda para poder priorizar la preparación entre ellos. */
    static final int RESULTADOS_BUSQUEDA = 10;
    static final String NOMBRE_ACEITE = "Aceite de oliva (estimado)";
    static final BigDecimal GRAMOS_ACEITE = new BigDecimal("10");
    private static final String BUSQUEDA_ACEITE = "aceite de oliva";
    private static final BigDecimal CONFIANZA_ALTA = new BigDecimal("0.80");
    private static final BigDecimal CONFIANZA_MEDIA = new BigDecimal("0.50");

    private final AlimentoRepositorio alimentoRepositorio;
    private final CalculadoraNutricional calculadoraNutricional;

    public EmparejadorCatalogo(AlimentoRepositorio alimentoRepositorio,
            CalculadoraNutricional calculadoraNutricional) {
        this.alimentoRepositorio = alimentoRepositorio;
        this.calculadoraNutricional = calculadoraNutricional;
    }

    /**
     * @return el candidato del alimento detectado y, si es FRITO, otro editable con 10 g de "Aceite de
     *         oliva (estimado)"
     */
    public List<CandidatoAnalisis> emparejar(AlimentoDetectado detectado, UUID usuarioId) {
        List<CoincidenciaCatalogo> coincidencias = buscar(detectado.nombre(), detectado.preparacion(),
                detectado.gramosEstimados(), usuarioId);
        for (int i = 0; coincidencias.isEmpty() && i < detectado.alternativas().size(); i++) {
            coincidencias = buscar(detectado.alternativas().get(i), detectado.preparacion(),
                    detectado.gramosEstimados(), usuarioId);
        }
        List<CandidatoAnalisis> candidatos = new ArrayList<>();
        candidatos.add(new CandidatoAnalisis(detectado.nombre(), detectado.preparacion(), detectado.gramosEstimados(),
                detectado.confianza(), nivelConfianza(detectado.confianza()), false, coincidencias));
        if (detectado.preparacion() == Preparacion.FRITO) {
            candidatos.add(new CandidatoAnalisis(NOMBRE_ACEITE, Preparacion.OTRA, GRAMOS_ACEITE, detectado.confianza(),
                    nivelConfianza(detectado.confianza()), true,
                    buscar(BUSQUEDA_ACEITE, null, GRAMOS_ACEITE, usuarioId)));
        }
        return candidatos;
    }

    /** ALTA (≥ 0,80), MEDIA (≥ 0,50) o BAJA. */
    static String nivelConfianza(BigDecimal confianza) {
        if (confianza != null && confianza.compareTo(CONFIANZA_ALTA) >= 0) {
            return "ALTA";
        }
        if (confianza != null && confianza.compareTo(CONFIANZA_MEDIA) >= 0) {
            return "MEDIA";
        }
        return "BAJA";
    }

    /**
     * Hasta 3 coincidencias: las de la misma preparación primero y, dentro de cada grupo, en el orden de
     * parecido de la búsqueda.
     */
    private List<CoincidenciaCatalogo> buscar(String nombre, Preparacion preparacion, BigDecimal gramos,
            UUID usuarioId) {
        String consulta = TextoNormalizado.paraBusqueda(nombre);
        if (consulta == null || consulta.length() < 2) {
            return List.of();
        }
        List<Alimento> encontrados = alimentoRepositorio
                .buscar(consulta, null, usuarioId, PageRequest.of(0, RESULTADOS_BUSQUEDA))
                .getContent();
        if (encontrados.isEmpty()) {
            return List.of();
        }
        Map<UUID, BigDecimal> similitudes = alimentoRepositorio
                .similitudes(consulta, encontrados.stream().map(Alimento::getId).toList()).stream()
                .collect(Collectors.toMap(SimilitudAlimento::getId,
                        similitud -> BigDecimal.valueOf(similitud.getSimilitud().doubleValue())
                                .setScale(2, RoundingMode.HALF_UP),
                        (primera, segunda) -> primera));
        // sort es estable: dentro de cada grupo se conserva el orden de parecido de la búsqueda
        return encontrados.stream()
                .sorted(Comparator.comparing((Alimento alimento) -> alimento.getPreparacion() != preparacion))
                .limit(MAXIMO_COINCIDENCIAS)
                .map(alimento -> new CoincidenciaCatalogo(alimento.getId(), alimento.getNombre(),
                        similitudes.getOrDefault(alimento.getId(), BigDecimal.ZERO),
                        calculadoraNutricional.deAlimento(alimento, gramos).redondear(),
                        alimento.getFuente().name()))
                .toList();
    }
}
