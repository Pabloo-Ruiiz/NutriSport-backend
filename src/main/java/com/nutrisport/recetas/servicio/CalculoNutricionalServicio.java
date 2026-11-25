package com.nutrisport.recetas.servicio;

import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.FuenteDato;
import com.nutrisport.catalogo.entidad.PorcionCasera;
import com.nutrisport.catalogo.repositorio.AlimentoRepositorio;
import com.nutrisport.catalogo.servicio.AnalizadorMedidas;
import com.nutrisport.catalogo.servicio.CalculadoraNutricional;
import com.nutrisport.catalogo.servicio.MapeoIngredientesServicio;
import com.nutrisport.catalogo.servicio.MedidaInterpretada;
import com.nutrisport.catalogo.servicio.NormalizacionAlimentosServicio;
import com.nutrisport.comun.dto.Nutrientes;
import com.nutrisport.comun.excepcion.ProveedorNoDisponibleException;
import com.nutrisport.comun.excepcion.RecursoNoEncontradoException;
import com.nutrisport.integracion.usda.DetalleUsdaDto;
import com.nutrisport.integracion.usda.FoodDataCentralServicio;
import com.nutrisport.perfil.entidad.Alergeno;
import com.nutrisport.recetas.entidad.EstadoConversion;
import com.nutrisport.recetas.entidad.EstadoNutricional;
import com.nutrisport.recetas.entidad.OrigenReceta;
import com.nutrisport.recetas.entidad.Receta;
import com.nutrisport.recetas.entidad.RecetaIngrediente;
import com.nutrisport.recetas.repositorio.RecetaRepositorio;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cálculo nutricional de una receta a partir de sus ingredientes (docs/API_INTEGRATIONS.md §7.1), con las reglas de
 * la guía:
 * <table>
 * <caption>Reglas</caption>
 * <tr><td>Emparejamiento</td><td>{@link MapeoIngredientesServicio} (MapeoIngrediente → catálogo → USDA). En las
 * recetas propias se conserva el alimento ya elegido.</td></tr>
 * <tr><td>Gramos</td><td>masa → directa (EXACTA); volumen → medidas caseras de USDA del alimento (EXACTA) o
 * densidad 1 (ESTIMADA); unidades → porción "unidad" del catálogo o "medium" de USDA (ESTIMADA).</td></tr>
 * <tr><td>Despreciable</td><td>pinch, to taste, garnish y handful de hierbas → 0 g.</td></tr>
 * <tr><td>Estado</td><td>COMPLETO (todo resuelto con medidas exactas) · APROXIMADO (≥ 90 % de la masa resuelta o
 * alguna estimada) · INCOMPLETO (&lt; 90 %).</td></tr>
 * <tr><td>Raciones</td><td>las de la receta (4 supuestas si racionesSupuestas).</td></tr>
 * <tr><td>Alérgenos</td><td>unión de los alimentos emparejados; verificados solo si todos los ingredientes están
 * emparejados y con los alérgenos revisados.</td></tr>
 * </table>
 * Un ingrediente está <b>resuelto</b> si tiene alimento y gramos. La completitud es la masa resuelta entre la masa
 * conocida de los ingredientes no despreciables. Si de algún ingrediente no despreciable no se conocen ni los
 * gramos, no se puede afirmar que se resuelva el 90 %: la completitud se queda como mucho en 89 % (INCOMPLETO).
 * <p>
 * Las llamadas a USDA (emparejamiento y medidas caseras) se hacen fuera de cualquier transacción propia.
 */
@Service
public class CalculoNutricionalServicio {

    static final BigDecimal UMBRAL_APROXIMADO = new BigDecimal("90");
    private static final BigDecimal CIEN = new BigDecimal("100");

    /** Datos de un ingrediente antes del cálculo. */
    private record Linea(UUID id, String nombreOriginal, String medida, UUID alimentoActual, BigDecimal gramosActuales,
            EstadoConversion estadoActual) {
    }

    /** Resultado del cálculo de un ingrediente. */
    private record Resultado(UUID alimentoId, BigDecimal gramos, EstadoConversion estado) {
    }

    private final RecetaRepositorio recetaRepositorio;
    private final AlimentoRepositorio alimentoRepositorio;
    private final MapeoIngredientesServicio mapeo;
    private final FoodDataCentralServicio usda;
    private final CalculadoraNutricional calculadora;
    private final TransactionTemplate escritura;
    private final TransactionTemplate lectura;

    public CalculoNutricionalServicio(RecetaRepositorio recetaRepositorio, AlimentoRepositorio alimentoRepositorio,
            MapeoIngredientesServicio mapeo, FoodDataCentralServicio usda, CalculadoraNutricional calculadora,
            PlatformTransactionManager transacciones) {
        this.recetaRepositorio = recetaRepositorio;
        this.alimentoRepositorio = alimentoRepositorio;
        this.mapeo = mapeo;
        this.usda = usda;
        this.calculadora = calculadora;
        this.escritura = new TransactionTemplate(transacciones);
        this.lectura = new TransactionTemplate(transacciones);
        this.lectura.setReadOnly(true);
    }

    /** Calcula y guarda los gramos de cada ingrediente, los nutrientes por ración, el estado y los alérgenos. */
    public void calcular(Receta receta) {
        calcular(receta.getId());
    }

    /** @throws RecursoNoEncontradoException si la receta no existe */
    public void calcular(UUID recetaId) {
        record Datos(OrigenReceta origen, List<Linea> lineas) {
        }
        Datos datos = lectura.execute(estado -> {
            Receta receta = recetaRepositorio.findWithIngredientesById(recetaId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("La receta no existe"));
            return new Datos(receta.getOrigen(), receta.getIngredientes().stream()
                    .map(i -> new Linea(i.getId(), i.getNombreOriginal(), i.getMedidaOriginal(),
                            i.getAlimento() == null ? null : i.getAlimento().getId(), i.getCantidadGramos(),
                            i.getEstadoConversion()))
                    .toList());
        });

        Map<UUID, Resultado> resultados = new HashMap<>();
        for (Linea linea : datos.lineas()) {
            resultados.put(linea.id(), resolver(datos.origen(), linea));
        }

        escritura.executeWithoutResult(estado -> {
            Receta receta = recetaRepositorio.findWithIngredientesById(recetaId).orElseThrow();
            Set<UUID> ids = resultados.values().stream().map(Resultado::alimentoId).filter(id -> id != null)
                    .collect(Collectors.toSet());
            Map<UUID, Alimento> alimentos = alimentoRepositorio.findAllById(ids).stream()
                    .collect(Collectors.toMap(Alimento::getId, Function.identity()));
            for (RecetaIngrediente ingrediente : receta.getIngredientes()) {
                Resultado resultado = resultados.get(ingrediente.getId());
                if (resultado != null) {
                    ingrediente.setAlimento(resultado.alimentoId() == null ? null : alimentos.get(resultado.alimentoId()));
                    ingrediente.setCantidadGramos(resultado.gramos());
                    ingrediente.setEstadoConversion(resultado.estado());
                }
            }
            aplicarTotales(receta);
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Un ingrediente
    // ---------------------------------------------------------------------------------------------

    private Resultado resolver(OrigenReceta origen, Linea linea) {
        Alimento alimento = alimento(origen, linea);
        UUID alimentoId = alimento == null ? null : alimento.getId();
        // En las propias, unos gramos ya fijados con exactitud se conservan
        if (origen == OrigenReceta.PROPIA && linea.gramosActuales() != null
                && linea.estadoActual() == EstadoConversion.EXACTA) {
            return new Resultado(alimentoId, linea.gramosActuales(), EstadoConversion.EXACTA);
        }
        MedidaInterpretada medida = AnalizadorMedidas.interpretar(linea.medida());
        return switch (medida.tipo()) {
            case DESPRECIABLE -> new Resultado(alimentoId, BigDecimal.ZERO.setScale(1), EstadoConversion.DESPRECIABLE);
            case MASA -> new Resultado(alimentoId, AnalizadorMedidas.gramosDeMasa(medida), EstadoConversion.EXACTA);
            case VOLUMEN -> volumen(alimentoId, alimento, medida);
            case UNIDAD -> unidad(alimentoId, alimento, medida, linea.nombreOriginal());
            case DESCONOCIDA -> new Resultado(alimentoId, null, EstadoConversion.SIN_DATOS);
        };
    }

    /** En las propias, el alimento elegido; en las de TheMealDB, el del emparejamiento (que puede cambiar). */
    private Alimento alimento(OrigenReceta origen, Linea linea) {
        if (origen == OrigenReceta.PROPIA && linea.alimentoActual() != null) {
            return lectura.execute(estado -> alimentoRepositorio.findById(linea.alimentoActual()).orElse(null));
        }
        return mapeo.resolver(linea.nombreOriginal()).orElse(null);
    }

    /** Medidas caseras de USDA del alimento (EXACTA) o densidad 1 (ESTIMADA). */
    private Resultado volumen(UUID alimentoId, Alimento alimento, MedidaInterpretada medida) {
        Set<String> nombres = AnalizadorMedidas.nombresDeVolumen(medida.unidad());
        Optional<PorcionCasera> porcion = porcionesUsda(alimento).stream()
                .filter(p -> contieneAlguna(p.nombre(), nombres))
                .findFirst();
        if (porcion.isPresent()) {
            return new Resultado(alimentoId, gramos(medida.cantidad(), porcion.get().gramos()), EstadoConversion.EXACTA);
        }
        return new Resultado(alimentoId, AnalizadorMedidas.mililitros(medida), EstadoConversion.ESTIMADA);
    }

    /** Porción "unidad…" del catálogo o "medium" de USDA (ESTIMADA); "handful" de hierbas, despreciable. */
    private Resultado unidad(UUID alimentoId, Alimento alimento, MedidaInterpretada medida, String nombreOriginal) {
        if ("handful".equals(medida.unidad())) {
            return AnalizadorMedidas.esHierba(nombreOriginal)
                    ? new Resultado(alimentoId, BigDecimal.ZERO.setScale(1), EstadoConversion.DESPRECIABLE)
                    : new Resultado(alimentoId, null, EstadoConversion.SIN_DATOS);
        }
        if (alimento == null) {
            return new Resultado(null, null, EstadoConversion.SIN_DATOS);
        }
        Optional<PorcionCasera> porcion = alimento.getPorciones().stream()
                .filter(p -> p.nombre().toLowerCase(Locale.ROOT).startsWith("unidad"))
                .findFirst()
                .or(() -> porcionesUsda(alimento).stream()
                        .filter(p -> contieneAlguna(p.nombre(), Set.of("medium")))
                        .findFirst());
        return porcion
                .map(p -> new Resultado(alimentoId, gramos(medida.cantidad(), p.gramos()), EstadoConversion.ESTIMADA))
                .orElseGet(() -> new Resultado(alimentoId, null, EstadoConversion.SIN_DATOS));
    }

    /**
     * Medidas caseras de un alimento de USDA: las guardadas al importarlo o, si no tiene, las del detalle
     * ({@code foodPortions}). Vacío si no es de USDA o USDA no está disponible.
     */
    private List<PorcionCasera> porcionesUsda(Alimento alimento) {
        if (alimento == null || alimento.getFuente() != FuenteDato.USDA) {
            return List.of();
        }
        if (!alimento.getPorciones().isEmpty()) {
            return alimento.getPorciones();
        }
        try {
            return usda.detalle(alimento.getReferenciaExterna())
                    .map(DetalleUsdaDto::porciones)
                    .map(NormalizacionAlimentosServicio::porcionesUsda)
                    .orElse(List.of());
        } catch (ProveedorNoDisponibleException e) {
            return List.of();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Totales de la receta
    // ---------------------------------------------------------------------------------------------

    /** Nutrientes por ración, completitud, estado, alérgenos y aptitudes a partir de los ingredientes ya calculados. */
    private void aplicarTotales(Receta receta) {
        List<RecetaIngrediente> ingredientes = receta.getIngredientes();
        List<RecetaIngrediente> relevantes = ingredientes.stream()
                .filter(i -> i.getEstadoConversion() != EstadoConversion.DESPRECIABLE)
                .toList();

        BigDecimal masaConocida = BigDecimal.ZERO;
        BigDecimal masaResuelta = BigDecimal.ZERO;
        boolean masaDesconocida = false;
        boolean todoExacto = !relevantes.isEmpty();
        Nutrientes total = Nutrientes.CERO;
        for (RecetaIngrediente ingrediente : relevantes) {
            BigDecimal gramos = ingrediente.getCantidadGramos();
            boolean resuelto = gramos != null && ingrediente.getAlimento() != null;
            if (gramos == null) {
                masaDesconocida = true;
            } else {
                masaConocida = masaConocida.add(gramos);
            }
            if (resuelto) {
                masaResuelta = masaResuelta.add(gramos);
                total = total.sumar(calculadora.deAlimento(ingrediente.getAlimento(), gramos));
            }
            todoExacto &= resuelto && ingrediente.getEstadoConversion() == EstadoConversion.EXACTA;
        }

        int completitud = masaConocida.signum() == 0 ? 0
                : masaResuelta.multiply(CIEN).divide(masaConocida, 0, RoundingMode.DOWN).intValue();
        if (masaDesconocida) {
            completitud = Math.min(completitud, UMBRAL_APROXIMADO.intValue() - 1);
        }
        EstadoNutricional estado = todoExacto ? EstadoNutricional.COMPLETO
                : completitud >= UMBRAL_APROXIMADO.intValue() ? EstadoNutricional.APROXIMADO
                : EstadoNutricional.INCOMPLETO;
        receta.setCompletitudPct((short) (todoExacto ? 100 : completitud));
        receta.setEstadoNutricional(estado);

        if (estado == EstadoNutricional.INCOMPLETO) {
            receta.setKcalRacion(null);
            receta.setProteinaRacion(null);
            receta.setCarbohidratosRacion(null);
            receta.setGrasaRacion(null);
        } else {
            BigDecimal raciones = BigDecimal.valueOf(receta.getRaciones());
            receta.setKcalRacion(porRacion(total.kcal(), raciones));
            receta.setProteinaRacion(porRacion(total.proteina(), raciones));
            receta.setCarbohidratosRacion(porRacion(total.carbohidratos(), raciones));
            receta.setGrasaRacion(porRacion(total.grasa(), raciones));
        }
        aplicarAlergenos(receta, ingredientes);
    }

    /** Unión de los alérgenos de los alimentos emparejados; verificados y aptitudes solo con todo emparejado. */
    private static void aplicarAlergenos(Receta receta, List<RecetaIngrediente> ingredientes) {
        Set<Alergeno> contiene = EnumSet.noneOf(Alergeno.class);
        Set<Alergeno> trazas = EnumSet.noneOf(Alergeno.class);
        boolean todosEmparejados = !ingredientes.isEmpty();
        boolean todosRevisados = true;
        boolean lactosa = false;
        boolean vegano = true;
        boolean vegetariano = true;
        boolean pescetariano = true;
        for (RecetaIngrediente ingrediente : ingredientes) {
            Alimento alimento = ingrediente.getAlimento();
            if (alimento == null) {
                todosEmparejados = false;
                continue;
            }
            alimento.getAlergenosContiene().forEach(a -> contiene.add(Alergeno.valueOf(a)));
            alimento.getAlergenosTrazas().forEach(a -> trazas.add(Alergeno.valueOf(a)));
            todosRevisados &= alimento.isAlergenosRevisados();
            lactosa |= alimento.isContieneLactosa();
            vegano &= alimento.isAptoVegano();
            vegetariano &= alimento.isAptoVegetariano();
            pescetariano &= alimento.isAptoPescetariano();
        }
        trazas.removeAll(contiene);
        receta.setAlergenosContiene(nombres(contiene));
        receta.setAlergenosTrazas(nombres(trazas));
        receta.setContieneLactosa(lactosa);
        receta.setAlergenosVerificados(todosEmparejados && todosRevisados);
        // Sin todos los ingredientes emparejados no se puede afirmar la aptitud para una dieta
        receta.setAptoVegano(todosEmparejados && vegano);
        receta.setAptoVegetariano(todosEmparejados && vegetariano);
        receta.setAptoPescetariano(todosEmparejados && pescetariano);
    }

    // ---------------------------------------------------------------------------------------------

    private static BigDecimal porRacion(BigDecimal total, BigDecimal raciones) {
        return total.divide(raciones, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal gramos(BigDecimal cantidad, BigDecimal gramosPorUnidad) {
        return cantidad.multiply(gramosPorUnidad).setScale(1, RoundingMode.HALF_UP);
    }

    /** Si alguna palabra del nombre de la porción ("cup, sliced") es una de las buscadas. */
    private static boolean contieneAlguna(String nombre, Set<String> palabras) {
        for (String palabra : nombre.toLowerCase(Locale.ROOT).split("[^a-z]+")) {
            if (palabras.contains(palabra)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> nombres(Set<Alergeno> alergenos) {
        return new ArrayList<>(alergenos.stream().map(Enum::name).toList());
    }
}
