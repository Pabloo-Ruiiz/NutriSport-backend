package com.nutrisport.comidas.servicio;

import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import com.nutrisport.auth.seguridad.SeguridadUtils;
import com.nutrisport.catalogo.dto.ProductoResponse;
import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.UnidadBase;
import com.nutrisport.catalogo.repositorio.AlimentoRepositorio;
import com.nutrisport.catalogo.servicio.CalculadoraNutricional;
import com.nutrisport.catalogo.servicio.NormalizacionAlimentosServicio;
import com.nutrisport.catalogo.servicio.ProductoServicio;
import com.nutrisport.comidas.dto.AlimentoRegistradoRequest;
import com.nutrisport.comidas.dto.CandidatoAnalisis;
import com.nutrisport.comidas.dto.ComidasDiaResponse;
import com.nutrisport.comidas.dto.DuplicarComidaRequest;
import com.nutrisport.comidas.dto.RegistroComidaRequest;
import com.nutrisport.comidas.dto.RegistroComidaResponse;
import com.nutrisport.comidas.entidad.AlimentoRegistrado;
import com.nutrisport.comidas.entidad.OrigenRegistro;
import com.nutrisport.comidas.entidad.RegistroComida;
import com.nutrisport.comidas.entidad.TipoComida;
import com.nutrisport.comidas.mapper.ComidaMapper;
import com.nutrisport.comidas.repositorio.RegistroComidaRepositorio;
import com.nutrisport.comun.dto.ErrorCampo;
import com.nutrisport.comun.dto.Nutrientes;
import com.nutrisport.comun.excepcion.PeticionInvalidaException;
import com.nutrisport.comun.excepcion.RecursoNoEncontradoException;
import com.nutrisport.comun.excepcion.ReglaNegocioException;
import com.nutrisport.perfil.servicio.ObjetivoDiarioServicio;
import com.nutrisport.perfil.servicio.RestriccionesServicio;
import com.nutrisport.perfil.servicio.RestriccionesUsuario;
import com.nutrisport.recetas.entidad.EstadoNutricional;
import com.nutrisport.recetas.entidad.Receta;
import com.nutrisport.recetas.entidad.RecetaIngrediente;
import com.nutrisport.recetas.repositorio.RecetaRepositorio;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro de comidas e historial (§3.C y §7.4). Todas las operaciones actúan sobre las comidas del
 * usuario autenticado: las de otro responden 404. Los nutrientes de cada alimento se calculan con el
 * catálogo y se guardan como instantánea (§6.3); los totales se recalculan en la misma transacción.
 * Al registrar una comida se crea el objetivo de ese día si aún no existe (y el usuario tiene perfil).
 */
@Service
@Transactional
public class ComidaServicio {

    static final int MAXIMO_DIAS_HISTORIAL = 92;
    static final BigDecimal MAXIMO_GRAMOS = new BigDecimal("5000");

    private static final String DATOS_NO_VALIDOS = "La petición contiene datos no válidos";
    private static final String NO_ENCONTRADA = "La comida no existe";

    private final RegistroComidaRepositorio registroRepositorio;
    private final AlimentoRepositorio alimentoRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final SeguridadUtils seguridadUtils;
    private final ConversorUnidades conversorUnidades;
    private final CalculadoraNutricional calculadoraNutricional;
    private final RestriccionesServicio restriccionesServicio;
    private final ComidaMapper comidaMapper;
    private final Clock reloj;
    private final ObjetivoDiarioServicio objetivoDiarioServicio;
    private final ProductoServicio productoServicio;
    private final RecetaRepositorio recetaRepositorio;

    public ComidaServicio(RegistroComidaRepositorio registroRepositorio, AlimentoRepositorio alimentoRepositorio,
            UsuarioRepositorio usuarioRepositorio, SeguridadUtils seguridadUtils, ConversorUnidades conversorUnidades,
            CalculadoraNutricional calculadoraNutricional, RestriccionesServicio restriccionesServicio,
            ComidaMapper comidaMapper, Clock reloj, ObjetivoDiarioServicio objetivoDiarioServicio,
            ProductoServicio productoServicio, RecetaRepositorio recetaRepositorio) {
        this.registroRepositorio = registroRepositorio;
        this.alimentoRepositorio = alimentoRepositorio;
        this.usuarioRepositorio = usuarioRepositorio;
        this.seguridadUtils = seguridadUtils;
        this.conversorUnidades = conversorUnidades;
        this.calculadoraNutricional = calculadoraNutricional;
        this.restriccionesServicio = restriccionesServicio;
        this.comidaMapper = comidaMapper;
        this.reloj = reloj;
        this.objetivoDiarioServicio = objetivoDiarioServicio;
        this.productoServicio = productoServicio;
        this.recetaRepositorio = recetaRepositorio;
    }

    // ---------------------------------------------------------------------------------------------
    // Escritura
    // ---------------------------------------------------------------------------------------------

    /**
     * Registra una comida. Los alimentos deben ser visibles para el usuario y estar activos. Los
     * conflictos con sus restricciones se devuelven como avisos, sin impedir el registro.
     * <p>
     * Las líneas con {@code codigoBarras} se resuelven con {@link ProductoServicio#porCodigo} (catálogo y, si
     * hace falta, Open Food Facts). Si todas vienen por código, el origen de un registro manual pasa a ser
     * CODIGO_BARRAS.
     */
    public RegistroComidaResponse crear(RegistroComidaRequest peticion, OrigenRegistro origen) {
        return crear(peticion, origen, null, null);
    }

    /** Registra una comida confirmada desde una foto y conserva la confianza de sus candidatos. */
    public RegistroComidaResponse crearDesdeFoto(RegistroComidaRequest peticion,
            List<CandidatoAnalisis> candidatos, List<Integer> indicesCandidatos) {
        return crear(peticion, OrigenRegistro.FOTO, candidatos, indicesCandidatos);
    }

    private RegistroComidaResponse crear(RegistroComidaRequest peticion, OrigenRegistro origen,
            List<CandidatoAnalisis> candidatos, List<Integer> indicesCandidatos) {
        UUID usuarioId = seguridadUtils.usuarioActualId();
        validarFecha(peticion.fecha(), "fecha");
        boolean todasPorCodigo = !peticion.alimentos().isEmpty()
                && peticion.alimentos().stream().allMatch(AlimentoRegistradoRequest::porCodigo);
        List<AlimentoRegistradoRequest> lineas = resolverCodigos(peticion.alimentos());
        RegistroComida registro = new RegistroComida();
        registro.setUsuario(usuarioRepositorio.getReferenceById(usuarioId));
        registro.setOrigen(origen == OrigenRegistro.MANUAL && todasPorCodigo ? OrigenRegistro.CODIGO_BARRAS : origen);
        copiarDatos(peticion, registro);
        anadirLineas(registro, lineas, usuarioId, Set.of(), candidatos, indicesCandidatos);
        registro.recalcularTotales();
        registroRepositorio.save(registro);
        objetivoDiarioServicio.crearSiNoExiste(usuarioId, registro.getFecha());
        return respuesta(registro, restriccionesServicio.estrictasDe(usuarioId));
    }

    /**
     * Sustituye los datos y los alimentos de la comida; el origen se conserva. Un alimento que ya estaba
     * en la comida se admite aunque se haya desactivado después.
     */
    public RegistroComidaResponse editar(UUID id, RegistroComidaRequest peticion) {
        UUID usuarioId = seguridadUtils.usuarioActualId();
        RegistroComida registro = comidaPropia(id, usuarioId);
        validarFecha(peticion.fecha(), "fecha");
        Set<UUID> yaRegistrados = registro.getAlimentos().stream()
                .map(linea -> linea.getAlimento().getId())
                .collect(Collectors.toSet());
        List<AlimentoRegistradoRequest> lineas = resolverCodigos(peticion.alimentos());
        copiarDatos(peticion, registro);
        registro.vaciarAlimentos();
        anadirLineas(registro, lineas, usuarioId, yaRegistrados, null, null);
        registro.recalcularTotales();
        objetivoDiarioServicio.crearSiNoExiste(usuarioId, registro.getFecha());
        return respuesta(registro, restriccionesServicio.estrictasDe(usuarioId));
    }

    public void eliminar(UUID id) {
        registroRepositorio.delete(comidaPropia(id, seguridadUtils.usuarioActualId()));
    }

    /**
     * Copia la comida en otra fecha (y opcionalmente otro tipo) con origen COPIA. Los nutrientes se
     * recalculan con los valores actuales del catálogo, porque es un registro nuevo.
     */
    public RegistroComidaResponse duplicar(UUID id, DuplicarComidaRequest peticion) {
        UUID usuarioId = seguridadUtils.usuarioActualId();
        RegistroComida original = comidaPropia(id, usuarioId);
        validarFecha(peticion.fecha(), "fecha");
        RegistroComida copia = new RegistroComida();
        copia.setUsuario(usuarioRepositorio.getReferenceById(usuarioId));
        copia.setOrigen(OrigenRegistro.COPIA);
        copia.setFecha(peticion.fecha());
        copia.setHora(original.getHora());
        copia.setTipoComida(peticion.tipoComida() != null ? peticion.tipoComida() : original.getTipoComida());
        copia.setNotas(original.getNotas());
        for (AlimentoRegistrado lineaOriginal : original.getAlimentos()) {
            copia.anadirAlimento(nuevaLinea(lineaOriginal.getAlimento(), lineaOriginal.getCantidad(),
                    lineaOriginal.getUnidad(), lineaOriginal.getGramos()));
        }
        copia.recalcularTotales();
        registroRepositorio.save(copia);
        objetivoDiarioServicio.crearSiNoExiste(usuarioId, copia.getFecha());
        return respuesta(copia, restriccionesServicio.estrictasDe(usuarioId));
    }

    /**
     * Registra como comida (origen RECETA) una receta visible para el usuario: sus ingredientes emparejados con el
     * catálogo, con los gramos escalados a {@code raciones / raciones de la receta}.
     *
     * @throws RecursoNoEncontradoException 404 si la receta no existe o es privada de otro usuario
     * @throws ReglaNegocioException        422 NUTRICION_INCOMPLETA si la receta tiene cálculo INCOMPLETO o ningún
     *                                      ingrediente emparejado con gramos
     */
    public RegistroComidaResponse registrarDesdeReceta(UUID recetaId, BigDecimal raciones, LocalDate fecha,
            TipoComida tipoComida) {
        UUID usuarioId = seguridadUtils.usuarioActualId();
        Receta receta = recetaRepositorio.findWithIngredientesById(recetaId)
                .filter(encontrada -> encontrada.esVisiblePara(usuarioId))
                .orElseThrow(() -> new RecursoNoEncontradoException("La receta no existe"));
        return registrarReceta(usuarioId, receta, raciones, fecha, tipoComida, null, OrigenRegistro.RECETA);
    }

    /**
     * Registra una comida planificada de un menú (origen PLAN), como {@link #registrarDesdeReceta}. La receta ya la
     * ha comprobado el menú; hay que llamarlo dentro de una transacción (ingredientes perezosos).
     */
    public RegistroComidaResponse registrarPlanificada(Receta receta, BigDecimal raciones, LocalDate fecha,
            TipoComida tipoComida, LocalTime hora) {
        return registrarReceta(seguridadUtils.usuarioActualId(), receta, raciones, fecha, tipoComida, hora,
                OrigenRegistro.PLAN);
    }

    private RegistroComidaResponse registrarReceta(UUID usuarioId, Receta receta, BigDecimal raciones, LocalDate fecha,
            TipoComida tipoComida, LocalTime hora, OrigenRegistro origen) {
        validarFecha(fecha, "fecha");
        if (receta.getEstadoNutricional() == EstadoNutricional.INCOMPLETO) {
            throw new ReglaNegocioException("NUTRICION_INCOMPLETA",
                    "La receta no tiene un cálculo nutricional completo y no se puede registrar");
        }
        BigDecimal factor = raciones.divide(BigDecimal.valueOf(receta.getRaciones()), 6, RoundingMode.HALF_UP);
        RegistroComida registro = new RegistroComida();
        registro.setUsuario(usuarioRepositorio.getReferenceById(usuarioId));
        registro.setOrigen(origen);
        registro.setFecha(fecha);
        registro.setHora(hora);
        registro.setTipoComida(tipoComida);
        registro.setNotas(notaDeReceta(receta, raciones));
        for (RecetaIngrediente ingrediente : receta.getIngredientes()) {
            if (ingrediente.getAlimento() == null || ingrediente.getCantidadGramos() == null) {
                continue;
            }
            BigDecimal gramos = ingrediente.getCantidadGramos().multiply(factor).setScale(1, RoundingMode.HALF_UP);
            if (gramos.signum() <= 0) {
                continue;
            }
            Alimento alimento = ingrediente.getAlimento();
            String unidad = alimento.getUnidadBase() == UnidadBase.ML ? "ml" : "g";
            registro.anadirAlimento(nuevaLinea(alimento, gramos, unidad, gramos));
        }
        if (registro.getAlimentos().isEmpty()) {
            throw new ReglaNegocioException("NUTRICION_INCOMPLETA",
                    "La receta no tiene ingredientes emparejados con el catálogo y no se puede registrar");
        }
        registro.recalcularTotales();
        registroRepositorio.save(registro);
        objetivoDiarioServicio.crearSiNoExiste(usuarioId, registro.getFecha());
        return respuesta(registro, restriccionesServicio.estrictasDe(usuarioId));
    }

    /** "Pollo con arroz · 1.5 raciones", recortado a las 500 letras de las notas. */
    private static String notaDeReceta(Receta receta, BigDecimal raciones) {
        String nota = receta.getNombre() + " · " + raciones.stripTrailingZeros().toPlainString()
                + (raciones.compareTo(BigDecimal.ONE) == 0 ? " ración" : " raciones");
        return nota.length() <= 500 ? nota : nota.substring(0, 500);
    }

    // ---------------------------------------------------------------------------------------------
    // Consulta
    // ---------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public RegistroComidaResponse obtener(UUID id) {
        UUID usuarioId = seguridadUtils.usuarioActualId();
        return respuesta(comidaPropia(id, usuarioId), restriccionesServicio.estrictasDe(usuarioId));
    }

    /** Comidas de un día en orden cronológico, con la suma de sus totales. */
    @Transactional(readOnly = true)
    public ComidasDiaResponse dia(LocalDate fecha) {
        return dia(seguridadUtils.usuarioActualId(), fecha);
    }

    /** Como {@link #dia(LocalDate)} para un usuario dado, sin depender del contexto de seguridad. */
    @Transactional(readOnly = true)
    public ComidasDiaResponse dia(UUID usuarioId, LocalDate fecha) {
        return resumenDia(fecha, registroRepositorio.findByUsuarioIdAndFecha(usuarioId, fecha),
                restriccionesServicio.estrictasDe(usuarioId));
    }

    /**
     * Historial agrupado por días (solo los días con comidas), en orden cronológico. El rango incluye
     * ambas fechas y no puede pasar de 92 días.
     */
    @Transactional(readOnly = true)
    public List<ComidasDiaResponse> listar(LocalDate desde, LocalDate hasta, TipoComida tipo) {
        return listar(seguridadUtils.usuarioActualId(), desde, hasta, tipo);
    }

    /**
     * Como {@link #listar(LocalDate, LocalDate, TipoComida)} para un usuario dado, sin depender del contexto de
     * seguridad.
     */
    @Transactional(readOnly = true)
    public List<ComidasDiaResponse> listar(UUID usuarioId, LocalDate desde, LocalDate hasta, TipoComida tipo) {
        if (hasta.isBefore(desde)) {
            throw invalida("hasta", "Debe ser igual o posterior a desde");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) >= MAXIMO_DIAS_HISTORIAL) {
            throw invalida("hasta", "El rango no puede superar los " + MAXIMO_DIAS_HISTORIAL + " días");
        }
        RestriccionesUsuario restricciones = restriccionesServicio.estrictasDe(usuarioId);
        Map<LocalDate, List<RegistroComida>> porDia = registroRepositorio
                .buscar(usuarioId, desde, hasta, tipo, Pageable.unpaged())
                .stream()
                .collect(Collectors.groupingBy(RegistroComida::getFecha, LinkedHashMap::new, Collectors.toList()));
        return porDia.entrySet().stream()
                .map(dia -> resumenDia(dia.getKey(), dia.getValue(), restricciones))
                .toList();
    }

    // ---------------------------------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------------------------------

    private RegistroComida comidaPropia(UUID id, UUID usuarioId) {
        return registroRepositorio.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADA));
    }

    private static void copiarDatos(RegistroComidaRequest peticion, RegistroComida registro) {
        registro.setFecha(peticion.fecha());
        registro.setHora(peticion.hora());
        registro.setTipoComida(peticion.tipoComida());
        registro.setNotas(peticion.notas() == null || peticion.notas().isBlank() ? null : peticion.notas().strip());
    }

    /**
     * Sustituye las líneas con código de barras por el alimento del catálogo correspondiente (un mismo código
     * se consulta una sola vez). Un producto con la información nutricional incompleta no se puede registrar:
     * 422 NUTRICION_INCOMPLETA. Un código desconocido da 404 y un proveedor caído sin copia local, 503.
     */
    private List<AlimentoRegistradoRequest> resolverCodigos(List<AlimentoRegistradoRequest> lineas) {
        Map<String, UUID> resueltos = new HashMap<>();
        return lineas.stream()
                .map(linea -> {
                    if (!linea.porCodigo()) {
                        return linea;
                    }
                    UUID alimentoId = resueltos.computeIfAbsent(linea.codigoBarras(), codigo -> {
                        ProductoResponse producto = productoServicio.porCodigo(codigo);
                        if (!producto.nutricionCompleta() || producto.id() == null) {
                            throw new ReglaNegocioException("NUTRICION_INCOMPLETA",
                                    NormalizacionAlimentosServicio.MENSAJE_NUTRICION_INCOMPLETA);
                        }
                        return producto.id();
                    });
                    return new AlimentoRegistradoRequest(alimentoId, linea.cantidad(), linea.unidad());
                })
                .toList();
    }

    /** Se admiten fechas pasadas y hasta mañana (comidas planificadas para el día siguiente). */
    private void validarFecha(LocalDate fecha, String campo) {
        if (fecha.isAfter(LocalDate.now(reloj).plusDays(1))) {
            throw invalida(campo, "No puede ser posterior a mañana");
        }
    }

    /**
     * Valida todas las líneas (acumulando los errores) y, si son correctas, las añade a la comida con su
     * instantánea nutricional.
     */
    private void anadirLineas(RegistroComida registro, List<AlimentoRegistradoRequest> lineas, UUID usuarioId,
            Set<UUID> inactivosAdmitidos, List<CandidatoAnalisis> candidatos, List<Integer> indicesCandidatos) {
        Set<UUID> ids = lineas.stream().map(AlimentoRegistradoRequest::alimentoId).collect(Collectors.toSet());
        Map<UUID, Alimento> alimentos = alimentoRepositorio.findAllById(ids).stream()
                .collect(Collectors.toMap(Alimento::getId, Function.identity()));

        List<ErrorCampo> errores = new ArrayList<>();
        List<AlimentoRegistrado> nuevas = new ArrayList<>();
        for (int i = 0; i < lineas.size(); i++) {
            AlimentoRegistradoRequest linea = lineas.get(i);
            String prefijo = "alimentos[" + i + "].";
            Alimento alimento = alimentos.get(linea.alimentoId());
            if (alimento == null || !alimento.esVisiblePara(usuarioId)) {
                errores.add(new ErrorCampo(prefijo + "alimentoId", "El alimento no existe"));
                continue;
            }
            if (!alimento.isActivo() && !inactivosAdmitidos.contains(alimento.getId())) {
                errores.add(new ErrorCampo(prefijo + "alimentoId", "El alimento ya no está disponible"));
                continue;
            }
            BigDecimal gramos;
            try {
                gramos = conversorUnidades.aGramos(alimento, linea.cantidad(), linea.unidad());
            } catch (PeticionInvalidaException e) {
                e.getErrores().forEach(error -> errores.add(new ErrorCampo(prefijo + error.campo(), error.mensaje())));
                continue;
            }
            if (gramos.signum() <= 0 || gramos.compareTo(MAXIMO_GRAMOS) > 0) {
                errores.add(new ErrorCampo(prefijo + "cantidad", "Equivale a " + gramos.toPlainString()
                        + " g; debe estar entre 0,1 y " + MAXIMO_GRAMOS.toPlainString() + " g"));
                continue;
            }
            AlimentoRegistrado nueva = nuevaLinea(alimento, linea.cantidad(), linea.unidad().strip(), gramos);
            if (indicesCandidatos != null && indicesCandidatos.get(i) != null) {
                CandidatoAnalisis candidato = candidatos.get(indicesCandidatos.get(i));
                nueva.setOrigenIa(true);
                nueva.setConfianzaIa(candidato.confianza());
            }
            nuevas.add(nueva);
        }
        if (!errores.isEmpty()) {
            throw new PeticionInvalidaException(DATOS_NO_VALIDOS, errores);
        }
        nuevas.forEach(registro::anadirAlimento);
    }

    /** Línea con la instantánea nutricional calculada con el catálogo y redondeada a un decimal. */
    private AlimentoRegistrado nuevaLinea(Alimento alimento, BigDecimal cantidad, String unidad, BigDecimal gramos) {
        Nutrientes nutrientes = calculadoraNutricional.deAlimento(alimento, gramos).redondear();
        AlimentoRegistrado linea = new AlimentoRegistrado();
        linea.setAlimento(alimento);
        linea.setCantidad(cantidad);
        linea.setUnidad(unidad);
        linea.setGramos(gramos);
        linea.setKcal(nutrientes.kcal());
        linea.setProteina(nutrientes.proteina());
        linea.setCarbohidratos(nutrientes.carbohidratos());
        linea.setGrasa(nutrientes.grasa());
        linea.setOrigenIa(false);
        return linea;
    }

    private RegistroComidaResponse respuesta(RegistroComida registro, RestriccionesUsuario restricciones) {
        return comidaMapper.aRespuesta(registro, avisos(registro, restricciones));
    }

    /** "Nombre del alimento: aviso", sin repetir. */
    private static List<String> avisos(RegistroComida registro, RestriccionesUsuario restricciones) {
        Set<String> avisos = new LinkedHashSet<>();
        for (AlimentoRegistrado linea : registro.getAlimentos()) {
            Alimento alimento = linea.getAlimento();
            restricciones.avisosPara(alimento).forEach(aviso -> avisos.add(alimento.getNombre() + ": " + aviso));
        }
        return List.copyOf(avisos);
    }

    private ComidasDiaResponse resumenDia(LocalDate fecha, List<RegistroComida> registros,
            RestriccionesUsuario restricciones) {
        Nutrientes totales = registros.stream()
                .map(registro -> new Nutrientes(registro.getTotalKcal(), registro.getTotalProteina(),
                        registro.getTotalCarbohidratos(), registro.getTotalGrasa()))
                .reduce(Nutrientes.CERO, Nutrientes::sumar)
                .redondear();
        List<RegistroComidaResponse> comidas = registros.stream()
                .map(registro -> respuesta(registro, restricciones))
                .toList();
        return new ComidasDiaResponse(fecha, comidas, totales);
    }

    private static PeticionInvalidaException invalida(String campo, String mensaje) {
        return new PeticionInvalidaException(DATOS_NO_VALIDOS, List.of(new ErrorCampo(campo, mensaje)));
    }
}
