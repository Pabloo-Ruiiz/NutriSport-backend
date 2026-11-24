package com.nutrisport.comidas.servicio;

import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import com.nutrisport.auth.seguridad.SeguridadUtils;
import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.repositorio.AlimentoRepositorio;
import com.nutrisport.catalogo.servicio.CalculadoraNutricional;
import com.nutrisport.comidas.dto.AlimentoRegistradoRequest;
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
import com.nutrisport.perfil.servicio.ObjetivoDiarioServicio;
import com.nutrisport.perfil.servicio.RestriccionesServicio;
import com.nutrisport.perfil.servicio.RestriccionesUsuario;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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

    public ComidaServicio(RegistroComidaRepositorio registroRepositorio, AlimentoRepositorio alimentoRepositorio,
            UsuarioRepositorio usuarioRepositorio, SeguridadUtils seguridadUtils, ConversorUnidades conversorUnidades,
            CalculadoraNutricional calculadoraNutricional, RestriccionesServicio restriccionesServicio,
            ComidaMapper comidaMapper, Clock reloj, ObjetivoDiarioServicio objetivoDiarioServicio) {
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
    }

    // ---------------------------------------------------------------------------------------------
    // Escritura
    // ---------------------------------------------------------------------------------------------

    /**
     * Registra una comida. Los alimentos deben ser visibles para el usuario y estar activos. Los
     * conflictos con sus restricciones se devuelven como avisos, sin impedir el registro.
     */
    public RegistroComidaResponse crear(RegistroComidaRequest peticion, OrigenRegistro origen) {
        UUID usuarioId = seguridadUtils.usuarioActualId();
        validarFecha(peticion.fecha(), "fecha");
        RegistroComida registro = new RegistroComida();
        registro.setUsuario(usuarioRepositorio.getReferenceById(usuarioId));
        registro.setOrigen(origen);
        copiarDatos(peticion, registro);
        anadirLineas(registro, peticion.alimentos(), usuarioId, Set.of());
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
        copiarDatos(peticion, registro);
        registro.vaciarAlimentos();
        anadirLineas(registro, peticion.alimentos(), usuarioId, yaRegistrados);
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
        UUID usuarioId = seguridadUtils.usuarioActualId();
        return resumenDia(fecha, registroRepositorio.findByUsuarioIdAndFecha(usuarioId, fecha),
                restriccionesServicio.estrictasDe(usuarioId));
    }

    /**
     * Historial agrupado por días (solo los días con comidas), en orden cronológico. El rango incluye
     * ambas fechas y no puede pasar de 92 días.
     */
    @Transactional(readOnly = true)
    public List<ComidasDiaResponse> listar(LocalDate desde, LocalDate hasta, TipoComida tipo) {
        if (hasta.isBefore(desde)) {
            throw invalida("hasta", "Debe ser igual o posterior a desde");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) >= MAXIMO_DIAS_HISTORIAL) {
            throw invalida("hasta", "El rango no puede superar los " + MAXIMO_DIAS_HISTORIAL + " días");
        }
        UUID usuarioId = seguridadUtils.usuarioActualId();
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
            Set<UUID> inactivosAdmitidos) {
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
            nuevas.add(nuevaLinea(alimento, linea.cantidad(), linea.unidad().strip(), gramos));
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
