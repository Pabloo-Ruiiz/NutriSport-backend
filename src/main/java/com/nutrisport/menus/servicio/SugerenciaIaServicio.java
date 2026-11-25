package com.nutrisport.menus.servicio;

import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import com.nutrisport.auth.seguridad.SeguridadUtils;
import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.Visibilidad;
import com.nutrisport.catalogo.repositorio.AlimentoRepositorio;
import com.nutrisport.catalogo.servicio.CalculadoraNutricional;
import com.nutrisport.comun.dto.Nutrientes;
import com.nutrisport.comun.excepcion.ConflictoException;
import com.nutrisport.comun.excepcion.RecursoNoEncontradoException;
import com.nutrisport.comun.excepcion.ReglaNegocioException;
import com.nutrisport.comun.util.TextoNormalizado;
import com.nutrisport.ia.entidad.FuncionIa;
import com.nutrisport.ia.puerto.GeneracionTextoPuerto;
import com.nutrisport.ia.puerto.ResultadoIa;
import com.nutrisport.ia.servicio.LimiteIaServicio;
import com.nutrisport.ia.servicio.UsoIaServicio;
import com.nutrisport.menus.dto.MenuResponse;
import com.nutrisport.menus.dto.SugerenciaIaResponse;
import com.nutrisport.menus.entidad.ComidaPlanificada;
import com.nutrisport.menus.entidad.MenuSemanal;
import com.nutrisport.menus.entidad.SugerenciaIa;
import com.nutrisport.menus.entidad.TipoSugerencia;
import com.nutrisport.menus.repositorio.ComidaPlanificadaRepositorio;
import com.nutrisport.menus.repositorio.MenuSemanalRepositorio;
import com.nutrisport.menus.repositorio.SugerenciaIaRepositorio;
import com.nutrisport.perfil.dto.ObjetivoDiarioResponse;
import com.nutrisport.perfil.servicio.ObjetivoDiarioServicio;
import com.nutrisport.perfil.servicio.RestriccionesServicio;
import com.nutrisport.perfil.servicio.RestriccionesUsuario;
import com.nutrisport.recetas.entidad.EstadoConversion;
import com.nutrisport.recetas.entidad.EstadoNutricional;
import com.nutrisport.recetas.entidad.OrigenReceta;
import com.nutrisport.recetas.entidad.Receta;
import com.nutrisport.recetas.entidad.RecetaIngrediente;
import com.nutrisport.recetas.repositorio.RecetaRepositorio;
import com.nutrisport.recetas.servicio.CalculoNutricionalServicio;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Personalización con IA de una comida planificada (§4.5, fila "Personalización con IA"): la IA propone un ajuste de
 * cantidad o una sustitución de un ingrediente con salida estructurada ({@link PropuestaAjusteIa}) y el backend
 * <ol>
 * <li>busca el alimento propuesto en el catálogo (nombre exacto sin tildes ni mayúsculas, público o propio y
 * activo),</li>
 * <li>comprueba las restricciones estrictas y los alérgenos ({@link RestriccionesUsuario#avisosPara}) y</li>
 * <li>recalcula los nutrientes con {@link CalculadoraNutricional}.</li>
 * </ol>
 * Si el alimento no existe o no cumple, la sugerencia se descarta ({@code valida = false}). Las cifras y el texto
 * los genera siempre el backend, nunca el modelo. Aplicar una sugerencia válida crea una receta PROPIA derivada
 * (copia privada con el ajuste) y la pone en la comida.
 * <p>
 * La llamada a la IA va fuera de las transacciones: se preparan los datos, se llama a la IA y se valida y guarda
 * con los datos vueltos a leer.
 */
@Service
public class SugerenciaIaServicio {

    static final String PLANTILLA = "sugerencia-menu";
    static final BigDecimal MAXIMO_GRAMOS = new BigDecimal("1000");
    static final String PREFIJO = "Sugerencia IA: ";
    private static final int LONGITUD_TEXTO = 500;
    private static final int LONGITUD_NOMBRE_RECETA = 150;
    private static final String SUFIJO_DERIVADA = " (ajustada)";
    private static final Locale ES = Locale.forLanguageTag("es-ES");
    /** Marcas de los bloques de la plantilla; se quitan de lo que escribe el usuario para que no pueda cerrarlos. */
    private static final Pattern MARCAS = Pattern.compile("</?\\s*(receta|dia|restricciones|preferencia_usuario)\\s*>",
            Pattern.CASE_INSENSITIVE);
    private static final String NO_ENCONTRADA = "La comida planificada no existe";
    private static final Logger LOG = LoggerFactory.getLogger(SugerenciaIaServicio.class);

    /** Resultado de validar una propuesta: o todo lo necesario para aplicarla, o el motivo del descarte. */
    private record Evaluacion(TipoSugerencia tipo, RecetaIngrediente ingrediente, Alimento alimento, BigDecimal gramos,
            BigDecimal deltaKcal, BigDecimal deltaProteina, String texto, boolean valida) {

        static Evaluacion descartada(TipoSugerencia tipo, String motivo) {
            return new Evaluacion(tipo, null, null, null, null, null, "Sugerencia IA descartada: " + motivo, false);
        }
    }

    private final ComidaPlanificadaRepositorio comidaRepositorio;
    private final MenuSemanalRepositorio menuRepositorio;
    private final SugerenciaIaRepositorio sugerenciaRepositorio;
    private final RecetaRepositorio recetaRepositorio;
    private final AlimentoRepositorio alimentoRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final ObjetivoDiarioServicio objetivoDiarioServicio;
    private final RestriccionesServicio restriccionesServicio;
    private final CalculadoraNutricional calculadora;
    private final CalculoNutricionalServicio calculoNutricional;
    private final MenuServicio menuServicio;
    private final GeneracionTextoPuerto generacionTexto;
    private final LimiteIaServicio limiteIaServicio;
    private final UsoIaServicio usoIaServicio;
    private final SeguridadUtils seguridadUtils;
    private final Clock reloj;
    private final TransactionTemplate escritura;

    public SugerenciaIaServicio(ComidaPlanificadaRepositorio comidaRepositorio, MenuSemanalRepositorio menuRepositorio,
            SugerenciaIaRepositorio sugerenciaRepositorio, RecetaRepositorio recetaRepositorio,
            AlimentoRepositorio alimentoRepositorio, UsuarioRepositorio usuarioRepositorio,
            ObjetivoDiarioServicio objetivoDiarioServicio, RestriccionesServicio restriccionesServicio,
            CalculadoraNutricional calculadora, CalculoNutricionalServicio calculoNutricional,
            MenuServicio menuServicio, GeneracionTextoPuerto generacionTexto, LimiteIaServicio limiteIaServicio,
            UsoIaServicio usoIaServicio, SeguridadUtils seguridadUtils, Clock reloj,
            PlatformTransactionManager transacciones) {
        this.comidaRepositorio = comidaRepositorio;
        this.menuRepositorio = menuRepositorio;
        this.sugerenciaRepositorio = sugerenciaRepositorio;
        this.recetaRepositorio = recetaRepositorio;
        this.alimentoRepositorio = alimentoRepositorio;
        this.usuarioRepositorio = usuarioRepositorio;
        this.objetivoDiarioServicio = objetivoDiarioServicio;
        this.restriccionesServicio = restriccionesServicio;
        this.calculadora = calculadora;
        this.calculoNutricional = calculoNutricional;
        this.menuServicio = menuServicio;
        this.generacionTexto = generacionTexto;
        this.limiteIaServicio = limiteIaServicio;
        this.usoIaServicio = usoIaServicio;
        this.seguridadUtils = seguridadUtils;
        this.reloj = reloj;
        this.escritura = new TransactionTemplate(transacciones);
    }

    // ---------------------------------------------------------------------------------------------
    // Sugerir
    // ---------------------------------------------------------------------------------------------

    /**
     * Pide a la IA un ajuste para la receta de la comida y lo valida. La IA recibe la receta (ingredientes con gramos),
     * el hueco energético y proteico del día y las restricciones, nunca datos personales.
     *
     * @throws RecursoNoEncontradoException 404 si la comida no es de un menú del usuario
     * @throws ReglaNegocioException        422 PERFIL_REQUERIDO sin perfil (no hay objetivo del día)
     * @throws com.nutrisport.comun.excepcion.ServicioNoDisponibleException 503 si la IA no responde
     */
    public SugerenciaIaResponse sugerirAjuste(UUID menuId, UUID comidaId, String preferenciaUsuario) {
        UUID usuarioId = seguridadUtils.usuarioActualId();
        Map<String, Object> variables = escritura.execute(estado -> variables(menuId, comidaId, usuarioId,
                preferenciaUsuario));
        limiteIaServicio.comprobar(usuarioId, FuncionIa.TEXTO);

        PropuestaAjusteIa propuesta = proponer(usuarioId, variables);
        return escritura.execute(estado -> guardar(menuId, comidaId, usuarioId, propuesta));
    }

    private Map<String, Object> variables(UUID menuId, UUID comidaId, UUID usuarioId, String preferencia) {
        ComidaPlanificada comida = comidaDelUsuario(menuId, comidaId, usuarioId);
        Receta receta = recetaConIngredientes(comida.getReceta().getId());
        ObjetivoDiarioResponse objetivo = objetivoDiarioServicio.obtener(usuarioId, comida.getFecha());
        Nutrientes planificado = comida.getMenu().getComidas().stream()
                .filter(otra -> otra.getFecha().equals(comida.getFecha()))
                .map(MenuServicio::nutrientes)
                .filter(Objects::nonNull)
                .reduce(Nutrientes.CERO, Nutrientes::sumar);
        Nutrientes total = objetivo.total();

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("receta", receta.getNombre());
        variables.put("raciones", receta.getRaciones());
        variables.put("racionesPlanificadas", numero(comida.getRaciones()));
        variables.put("ingredientes", receta.getIngredientes().stream()
                .map(ingrediente -> "- " + ingrediente.getNombreOriginal() + ": "
                        + (ingrediente.getCantidadGramos() == null ? "cantidad desconocida"
                                : numero(ingrediente.getCantidadGramos()) + " g"))
                .collect(Collectors.joining("\n")));
        variables.put("huecoKcal", numero(total.kcal().subtract(planificado.kcal()).setScale(0, RoundingMode.HALF_UP)));
        variables.put("huecoProteina",
                numero(total.proteina().subtract(planificado.proteina()).setScale(0, RoundingMode.HALF_UP)));
        variables.put("restricciones", GeneradorMenuServicio.describir(restriccionesServicio.estrictasDe(usuarioId)));
        variables.put("preferencia", preferenciaSegura(preferencia));
        return variables;
    }

    /** Llama a la IA y registra el uso; un fallo de la IA se propaga (503). */
    private PropuestaAjusteIa proponer(UUID usuarioId, Map<String, Object> variables) {
        long inicio = System.nanoTime();
        try {
            ResultadoIa<PropuestaAjusteIa> resultado = generacionTexto.generarEstructurado(PLANTILLA, variables,
                    PropuestaAjusteIa.class);
            usoIaServicio.registrar(usuarioId, FuncionIa.TEXTO, resultado, milisegundosDesde(inicio), true);
            return resultado.contenido();
        } catch (RuntimeException e) {
            try {
                usoIaServicio.registrar(usuarioId, FuncionIa.TEXTO, null, milisegundosDesde(inicio), false);
            } catch (RuntimeException registro) {
                LOG.warn("No se ha podido registrar el uso fallido de la IA: {}", registro.toString());
            }
            throw e;
        }
    }

    /** Vuelve a leer la comida y la receta (pueden haber cambiado durante la llamada), valida y guarda. */
    private SugerenciaIaResponse guardar(UUID menuId, UUID comidaId, UUID usuarioId, PropuestaAjusteIa propuesta) {
        ComidaPlanificada comida = comidaDelUsuario(menuId, comidaId, usuarioId);
        Receta receta = recetaConIngredientes(comida.getReceta().getId());
        Evaluacion evaluacion = evaluar(propuesta, receta, usuarioId, restriccionesServicio.estrictasDe(usuarioId));

        SugerenciaIa sugerencia = new SugerenciaIa();
        sugerencia.setUsuario(usuarioRepositorio.getReferenceById(usuarioId));
        sugerencia.setComida(comida);
        sugerencia.setReceta(receta);
        sugerencia.setTipo(evaluacion.tipo());
        sugerencia.setIngrediente(evaluacion.ingrediente());
        sugerencia.setAlimento(evaluacion.alimento());
        sugerencia.setGramos(evaluacion.gramos());
        sugerencia.setDeltaKcalRacion(evaluacion.deltaKcal());
        sugerencia.setDeltaProteinaRacion(evaluacion.deltaProteina());
        sugerencia.setTexto(recortar(evaluacion.texto(), LONGITUD_TEXTO));
        sugerencia.setValida(evaluacion.valida());
        sugerencia = sugerenciaRepositorio.save(sugerencia);
        return new SugerenciaIaResponse(sugerencia.getId(), sugerencia.getTexto(), sugerencia.getDeltaKcalRacion(),
                sugerencia.getDeltaProteinaRacion(), sugerencia.isValida());
    }

    /** Las comprobaciones de la propuesta, en orden; la primera que falla da el motivo del descarte. */
    private Evaluacion evaluar(PropuestaAjusteIa propuesta, Receta receta, UUID usuarioId,
            RestriccionesUsuario restricciones) {
        if (propuesta == null || propuesta.tipo() == null) {
            return Evaluacion.descartada(null, "la IA no ha propuesto ningún cambio utilizable");
        }
        TipoSugerencia tipo = propuesta.tipo();
        if (receta.getEstadoNutricional() != EstadoNutricional.COMPLETO
                && receta.getEstadoNutricional() != EstadoNutricional.APROXIMADO) {
            return Evaluacion.descartada(tipo, "la receta no tiene cálculo nutricional");
        }
        BigDecimal gramos = propuesta.gramos();
        if (gramos == null || gramos.signum() <= 0 || gramos.compareTo(MAXIMO_GRAMOS) > 0) {
            return Evaluacion.descartada(tipo, "la cantidad propuesta no es válida (de 1 a 1000 g)");
        }
        gramos = gramos.setScale(1, RoundingMode.HALF_UP);
        Optional<RecetaIngrediente> encontrado = ingrediente(receta, propuesta.ingredienteOriginal());
        if (encontrado.isEmpty()) {
            return Evaluacion.descartada(tipo, "el ingrediente «" + texto(propuesta.ingredienteOriginal())
                    + "» no está en la receta");
        }
        RecetaIngrediente ingrediente = encontrado.get();

        Alimento alimento;
        if (tipo == TipoSugerencia.AJUSTE_CANTIDAD) {
            alimento = ingrediente.getAlimento();
            if (alimento == null) {
                return Evaluacion.descartada(tipo, "el ingrediente «" + ingrediente.getNombreOriginal()
                        + "» no está emparejado con un alimento del catálogo");
            }
            String propuestoNormalizado = normalizado(propuesta.alimentoPropuesto());
            if (!propuestoNormalizado.isEmpty() && !propuestoNormalizado.equals(alimento.getNombreNormalizado())
                    && !propuestoNormalizado.equals(normalizado(ingrediente.getNombreOriginal()))) {
                return Evaluacion.descartada(tipo, "un ajuste de cantidad no puede cambiar el alimento");
            }
            if (ingrediente.getCantidadGramos() != null && ingrediente.getCantidadGramos().compareTo(gramos) == 0) {
                return Evaluacion.descartada(tipo, "la cantidad propuesta es la que ya tiene la receta");
            }
        } else {
            Optional<Alimento> delCatalogo = alimentoDelCatalogo(propuesta.alimentoPropuesto(), usuarioId);
            if (delCatalogo.isEmpty()) {
                return Evaluacion.descartada(tipo, "el alimento propuesto «" + texto(propuesta.alimentoPropuesto())
                        + "» no está en el catálogo");
            }
            alimento = delCatalogo.get();
            if (ingrediente.getAlimento() != null && alimento.getId().equals(ingrediente.getAlimento().getId())) {
                return Evaluacion.descartada(tipo, "el alimento propuesto es el mismo que el ingrediente");
            }
        }
        if (alimento.getKcal100() == null || alimento.getProteinas100() == null
                || alimento.getCarbohidratos100() == null || alimento.getGrasas100() == null) {
            return Evaluacion.descartada(tipo, "«" + alimento.getNombre() + "» no tiene información nutricional");
        }
        List<String> avisos = restricciones.avisosPara(alimento);
        if (!avisos.isEmpty()) {
            return Evaluacion.descartada(tipo, "«" + alimento.getNombre() + "» no cumple tus restricciones ("
                    + String.join(", ", avisos).toLowerCase(ES) + ")");
        }

        // Cifras del backend: aporte nuevo − aporte actual del ingrediente, por ración de la receta
        Nutrientes actual = ingrediente.getAlimento() != null && ingrediente.getCantidadGramos() != null
                ? calculadora.deAlimento(ingrediente.getAlimento(), ingrediente.getCantidadGramos())
                : Nutrientes.CERO;
        Nutrientes nuevo = calculadora.deAlimento(alimento, gramos);
        BigDecimal raciones = BigDecimal.valueOf(receta.getRaciones());
        BigDecimal deltaKcal = nuevo.kcal().subtract(actual.kcal()).divide(raciones, 1, RoundingMode.HALF_UP);
        BigDecimal deltaProteina = nuevo.proteina().subtract(actual.proteina()).divide(raciones, 1, RoundingMode.HALF_UP);

        String cambio = tipo == TipoSugerencia.AJUSTE_CANTIDAD
                ? "cambiar «" + ingrediente.getNombreOriginal() + "» de " + gramosTexto(ingrediente.getCantidadGramos())
                        + " a " + numero(gramos) + " g"
                : "sustituir «" + ingrediente.getNombreOriginal() + "» (" + gramosTexto(ingrediente.getCantidadGramos())
                        + ") por " + numero(gramos) + " g de " + alimento.getNombre();
        String texto = PREFIJO + cambio + " en «" + receta.getNombre() + "»: " + conSigno(deltaKcal) + " kcal y "
                + conSigno(deltaProteina) + " g de proteína por ración.";
        return new Evaluacion(tipo, ingrediente, alimento, gramos, deltaKcal, deltaProteina, texto, true);
    }

    // ---------------------------------------------------------------------------------------------
    // Aplicar
    // ---------------------------------------------------------------------------------------------

    /**
     * Crea una receta PROPIA derivada (copia privada, publicada para el planificador, con el ajuste y sus nutrientes
     * recalculados) y la pone en la comida planificada.
     *
     * @throws RecursoNoEncontradoException 404 si la sugerencia no es del usuario o de esa comida
     * @throws ReglaNegocioException        422 SUGERENCIA_NO_VALIDA si se descartó o ya no cumple las restricciones
     * @throws ConflictoException           409 SUGERENCIA_APLICADA, COMIDA_CONSUMIDA o SUGERENCIA_OBSOLETA (la comida
     *                                      ya tiene otra receta)
     */
    public MenuResponse aplicar(UUID menuId, UUID comidaId, UUID sugerenciaId) {
        UUID usuarioId = seguridadUtils.usuarioActualId();
        return escritura.execute(estado -> {
            SugerenciaIa sugerencia = sugerenciaRepositorio.buscarDelUsuario(sugerenciaId, menuId, comidaId, usuarioId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("La sugerencia no existe"));
            if (!sugerencia.isValida()) {
                throw new ReglaNegocioException("SUGERENCIA_NO_VALIDA", "La sugerencia se descartó y no se puede aplicar");
            }
            if (sugerencia.getAplicadaEn() != null) {
                throw new ConflictoException("SUGERENCIA_APLICADA", "La sugerencia ya se ha aplicado");
            }
            ComidaPlanificada comida = sugerencia.getComida();
            if (comida.isConsumida()) {
                throw new ConflictoException("COMIDA_CONSUMIDA", "La comida ya está consumida y no se puede cambiar");
            }
            if (!comida.getReceta().getId().equals(sugerencia.getReceta().getId())) {
                throw new ConflictoException("SUGERENCIA_OBSOLETA",
                        "La comida ya tiene otra receta: pide una sugerencia nueva");
            }
            Alimento alimento = sugerencia.getAlimento();
            if (!alimento.isActivo() || !restriccionesServicio.estrictasDe(usuarioId).avisosPara(alimento).isEmpty()) {
                throw new ReglaNegocioException("SUGERENCIA_NO_VALIDA",
                        "El alimento propuesto ya no está disponible o no cumple tus restricciones");
            }

            Receta original = recetaConIngredientes(sugerencia.getReceta().getId());
            Receta derivada = derivar(original, sugerencia, usuarioRepositorio.getReferenceById(usuarioId));
            calculoNutricional.recalcularTotales(derivada);
            derivada = recetaRepositorio.save(derivada);

            comida.setReceta(derivada);
            sugerencia.setRecetaDerivada(derivada);
            sugerencia.setAplicadaEn(OffsetDateTime.now(reloj));

            MenuSemanal menu = menuRepositorio.findByIdAndUsuarioId(menuId, usuarioId)
                    .orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADA));
            return menuServicio.respuesta(menu, usuarioId);
        });
    }

    /** Copia privada del usuario con los ingredientes de la original y el de la sugerencia cambiado. */
    private static Receta derivar(Receta original, SugerenciaIa sugerencia, Usuario usuario) {
        Receta copia = new Receta();
        copia.setOrigen(OrigenReceta.PROPIA);
        copia.setNombre(recortar(original.getNombre(), LONGITUD_NOMBRE_RECETA - SUFIJO_DERIVADA.length())
                + SUFIJO_DERIVADA);
        copia.setDescripcion(original.getDescripcion());
        copia.setIdioma(original.getIdioma());
        copia.setCategoria(original.getCategoria());
        copia.setCategoriaExterna(original.getCategoriaExterna());
        copia.setArea(original.getArea());
        copia.setImagenUrl(original.getImagenUrl());
        copia.setRaciones(original.getRaciones());
        copia.setRacionesSupuestas(original.isRacionesSupuestas());
        copia.setTiempoPreparacionMin(original.getTiempoPreparacionMin());
        copia.setDificultad(original.getDificultad());
        copia.setFranjasAptas(new ArrayList<>(original.getFranjasAptas()));
        copia.setPasos(new ArrayList<>(original.getPasos()));
        copia.setEtiquetas(new ArrayList<>(original.getEtiquetas()));
        copia.setVisibilidad(Visibilidad.PRIVADO);
        copia.setPropietario(usuario);
        // Solo la ve su propietario; publicada para que el generador de menús pueda usarla
        copia.setPublicada(true);

        UUID cambiado = sugerencia.getIngrediente().getId();
        for (RecetaIngrediente ingrediente : original.getIngredientes()) {
            RecetaIngrediente nuevo = new RecetaIngrediente(ingrediente.getNombreOriginal(),
                    ingrediente.getMedidaOriginal());
            nuevo.setAlimento(ingrediente.getAlimento());
            nuevo.setCantidadGramos(ingrediente.getCantidadGramos());
            nuevo.setEstadoConversion(ingrediente.getEstadoConversion());
            if (ingrediente.getId().equals(cambiado)) {
                Alimento alimento = sugerencia.getAlimento();
                if (sugerencia.getTipo() == TipoSugerencia.SUSTITUCION) {
                    nuevo.setNombreOriginal(recortar(alimento.getNombre(), 100));
                }
                nuevo.setAlimento(alimento);
                nuevo.setMedidaOriginal(numero(sugerencia.getGramos()) + " g");
                nuevo.setCantidadGramos(sugerencia.getGramos());
                nuevo.setEstadoConversion(EstadoConversion.EXACTA);
            }
            copia.anadirIngrediente(nuevo);
        }
        return copia;
    }

    // ---------------------------------------------------------------------------------------------

    private ComidaPlanificada comidaDelUsuario(UUID menuId, UUID comidaId, UUID usuarioId) {
        return comidaRepositorio.buscarDelUsuario(comidaId, menuId, usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADA));
    }

    private Receta recetaConIngredientes(UUID recetaId) {
        return recetaRepositorio.findWithIngredientesById(recetaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("La receta no existe"));
    }

    /** El ingrediente por su nombre original o el de su alimento, sin tildes ni mayúsculas. */
    private static Optional<RecetaIngrediente> ingrediente(Receta receta, String nombre) {
        String buscado = normalizado(nombre);
        if (buscado.isEmpty()) {
            return Optional.empty();
        }
        return receta.getIngredientes().stream()
                .filter(ingrediente -> buscado.equals(normalizado(ingrediente.getNombreOriginal()))
                        || (ingrediente.getAlimento() != null
                                && buscado.equals(ingrediente.getAlimento().getNombreNormalizado())))
                .findFirst();
    }

    private Optional<Alimento> alimentoDelCatalogo(String nombre, UUID usuarioId) {
        String buscado = normalizado(nombre);
        if (buscado.isEmpty()) {
            return Optional.empty();
        }
        return alimentoRepositorio.porNombreVisible(buscado, usuarioId).stream().findFirst();
    }

    private static String normalizado(String texto) {
        return texto == null || texto.isBlank() ? "" : TextoNormalizado.normalizar(texto.strip());
    }

    /** La preferencia sin las marcas de los bloques de la plantilla, como mucho 300 caracteres. */
    static String preferenciaSegura(String preferencia) {
        if (preferencia == null || preferencia.isBlank()) {
            return "(sin preferencia)";
        }
        return recortar(MARCAS.matcher(preferencia).replaceAll(" ").strip(), 300);
    }

    /** Texto de la IA para un mensaje: sin saltos de línea y corto. */
    private static String texto(String valor) {
        return valor == null ? "" : recortar(valor.replaceAll("\\s+", " ").strip(), 60);
    }

    private static String recortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }

    private static String gramosTexto(BigDecimal gramos) {
        return gramos == null ? "cantidad desconocida" : numero(gramos) + " g";
    }

    /** 120.0 → "120"; 67.5 → "67,5". */
    private static String numero(BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static String conSigno(BigDecimal valor) {
        return (valor.signum() >= 0 ? "+" : "") + numero(valor.setScale(1, RoundingMode.HALF_UP));
    }

    private static long milisegundosDesde(long inicioNanos) {
        return (System.nanoTime() - inicioNanos) / 1_000_000;
    }
}
