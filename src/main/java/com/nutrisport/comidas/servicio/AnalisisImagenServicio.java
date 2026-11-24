package com.nutrisport.comidas.servicio;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nutrisport.almacenamiento.AlmacenamientoProperties;
import com.nutrisport.almacenamiento.AlmacenamientoServicio;
import com.nutrisport.almacenamiento.ClavesAlmacenamiento;
import com.nutrisport.almacenamiento.ImagenProcesada;
import com.nutrisport.almacenamiento.ProcesadorImagenes;
import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import com.nutrisport.auth.seguridad.SeguridadUtils;
import com.nutrisport.comidas.dto.AnalisisResponse;
import com.nutrisport.comidas.dto.AlimentoConfirmadoRequest;
import com.nutrisport.comidas.dto.AlimentoRegistradoRequest;
import com.nutrisport.comidas.dto.CandidatoAnalisis;
import com.nutrisport.comidas.dto.ConfirmarAnalisisRequest;
import com.nutrisport.comidas.dto.RegistroComidaRequest;
import com.nutrisport.comidas.dto.RegistroComidaResponse;
import com.nutrisport.comidas.dto.UrlImagenResponse;
import com.nutrisport.comidas.entidad.AnalisisImagen;
import com.nutrisport.comidas.entidad.EstadoAnalisis;
import com.nutrisport.comidas.entidad.TipoAnalisis;
import com.nutrisport.comidas.entidad.TipoComida;
import com.nutrisport.comidas.repositorio.AnalisisImagenRepositorio;
import com.nutrisport.comidas.repositorio.RegistroComidaRepositorio;
import com.nutrisport.comun.dto.ErrorCampo;
import com.nutrisport.comun.dto.Nutrientes;
import com.nutrisport.comun.excepcion.ConflictoException;
import com.nutrisport.comun.excepcion.PeticionInvalidaException;
import com.nutrisport.comun.excepcion.RecursoNoEncontradoException;
import com.nutrisport.ia.entidad.FuncionIa;
import com.nutrisport.ia.servicio.LimiteIaServicio;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Análisis de fotos de comidas (§4.3 y §7.4). Crear un análisis valida y reprocesa la foto (sin EXIF),
 * la guarda en el almacenamiento y deja el análisis PENDIENTE; ProcesadorAnalisis lo completa en segundo
 * plano. Los análisis de otro usuario responden 404.
 */
@Service
@Transactional
public class AnalisisImagenServicio {

    static final int LONGITUD_MAXIMA_DESCRIPCION = 500;

    private static final String DATOS_NO_VALIDOS = "La petición contiene datos no válidos";
    private static final String NO_ENCONTRADO = "El análisis no existe";
    private static final String CONTENT_TYPE_JPEG = "image/jpeg";

    private final AnalisisImagenRepositorio analisisRepositorio;
    private final RegistroComidaRepositorio registroComidaRepositorio;
    private final ComidaServicio comidaServicio;
    private final CalculadoraCorrecciones calculadoraCorrecciones;
    private final UsuarioRepositorio usuarioRepositorio;
    private final SeguridadUtils seguridadUtils;
    private final LimiteIaServicio limiteIaServicio;
    private final ProcesadorImagenes procesadorImagenes;
    private final AlmacenamientoServicio almacenamiento;
    private final AlmacenamientoProperties propiedadesAlmacenamiento;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;

    public AnalisisImagenServicio(AnalisisImagenRepositorio analisisRepositorio,
            RegistroComidaRepositorio registroComidaRepositorio, ComidaServicio comidaServicio,
            CalculadoraCorrecciones calculadoraCorrecciones,
            UsuarioRepositorio usuarioRepositorio, SeguridadUtils seguridadUtils,
            LimiteIaServicio limiteIaServicio, ProcesadorImagenes procesadorImagenes,
            AlmacenamientoServicio almacenamiento, AlmacenamientoProperties propiedadesAlmacenamiento,
            ApplicationEventPublisher eventos, Clock reloj) {
        this.analisisRepositorio = analisisRepositorio;
        this.registroComidaRepositorio = registroComidaRepositorio;
        this.comidaServicio = comidaServicio;
        this.calculadoraCorrecciones = calculadoraCorrecciones;
        this.usuarioRepositorio = usuarioRepositorio;
        this.seguridadUtils = seguridadUtils;
        this.limiteIaServicio = limiteIaServicio;
        this.procesadorImagenes = procesadorImagenes;
        this.almacenamiento = almacenamiento;
        this.propiedadesAlmacenamiento = propiedadesAlmacenamiento;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    /**
     * Crea un análisis PENDIENTE. Antes comprueba el email verificado y el límite diario de análisis, y
     * valida y reprocesa la foto (415 si no es JPEG ni PNG). El análisis se procesa tras el commit.
     */
    public AnalisisResponse crear(MultipartFile imagen, TipoComida tipoComida, String descripcion) {
        UUID usuarioId = seguridadUtils.usuarioActualId();
        validar(imagen, tipoComida, descripcion);
        limiteIaServicio.comprobar(usuarioId, FuncionIa.ANALISIS_IMAGEN);

        ImagenProcesada procesada = procesadorImagenes.procesar(bytes(imagen));
        String claveImagen = ClavesAlmacenamiento.claveComida(usuarioId, LocalDate.now(reloj));
        String claveMiniatura = ClavesAlmacenamiento.claveMiniatura(claveImagen);
        almacenamiento.guardar(claveImagen, procesada.imagen(), CONTENT_TYPE_JPEG);
        almacenamiento.guardar(claveMiniatura, procesada.miniatura(), CONTENT_TYPE_JPEG);

        AnalisisImagen analisis = new AnalisisImagen();
        analisis.setUsuario(usuarioRepositorio.getReferenceById(usuarioId));
        analisis.setTipo(TipoAnalisis.COMIDA);
        analisis.setEstado(EstadoAnalisis.PENDIENTE);
        analisis.setTipoComida(tipoComida);
        analisis.setDescripcionUsuario(descripcion == null || descripcion.isBlank() ? null : descripcion.strip());
        analisis.setImagenKey(claveImagen);
        analisis.setMiniaturaKey(claveMiniatura);
        analisisRepositorio.save(analisis);
        eventos.publishEvent(new AnalisisCreadoEvento(analisis.getId()));
        return respuesta(analisis);
    }

    /** Estado y resultado del análisis (para el polling del frontend). */
    @Transactional(readOnly = true)
    public AnalisisResponse obtener(UUID id) {
        return respuesta(propio(id));
    }

    /** URL prefirmada de la foto; 404 si el análisis es ajeno o la foto ya se borró por retención. */
    @Transactional(readOnly = true)
    public UrlImagenResponse urlImagen(UUID id) {
        AnalisisImagen analisis = propio(id);
        if (analisis.getImagenKey() == null) {
            throw new RecursoNoEncontradoException("La foto de este análisis ya no está disponible");
        }
        return new UrlImagenResponse(almacenamiento.urlLectura(analisis.getImagenKey()),
                OffsetDateTime.now(reloj).plusMinutes(propiedadesAlmacenamiento.urlMinutos()));
    }

    /** Confirma los candidatos revisados y registra una comida cuya información ha decidido el usuario. */
    public RegistroComidaResponse confirmar(UUID id, ConfirmarAnalisisRequest peticion) {
        AnalisisImagen analisis = propio(id);
        if (analisis.getEstado() != EstadoAnalisis.COMPLETADO) {
            throw new ConflictoException("ANALISIS_NO_CONFIRMABLE",
                    "Solo se pueden confirmar análisis completados");
        }
        List<CandidatoAnalisis> candidatos = analisis.getCandidatos();
        List<Integer> indices = peticion.alimentos().stream()
                .map(AlimentoConfirmadoRequest::indiceCandidato)
                .toList();
        validarIndicesCandidatos(indices, candidatos.size());

        Correcciones correcciones = calculadoraCorrecciones.calcular(candidatos, peticion.alimentos());
        List<AlimentoRegistradoRequest> alimentos = peticion.alimentos().stream()
                .map(alimento -> new AlimentoRegistradoRequest(alimento.alimentoId(), alimento.gramos(), "g"))
                .toList();
        RegistroComidaRequest solicitudComida = new RegistroComidaRequest(peticion.fecha(), peticion.hora(),
                peticion.tipoComida(), null, alimentos);
        RegistroComidaResponse comida = comidaServicio.crearDesdeFoto(solicitudComida, candidatos, indices);

        analisis.setCorrecciones(aJson(correcciones));
        analisis.setRegistroComida(registroComidaRepositorio.getReferenceById(comida.id()));
        analisis.setEstado(EstadoAnalisis.CONFIRMADO);
        return comida;
    }

    /** Descarta un análisis terminado, tanto si produjo candidatos como si falló. */
    public void descartar(UUID id) {
        AnalisisImagen analisis = propio(id);
        if (analisis.getEstado() != EstadoAnalisis.COMPLETADO && analisis.getEstado() != EstadoAnalisis.FALLIDO) {
            throw new ConflictoException("ANALISIS_NO_DESCARTABLE",
                    "Solo se pueden descartar análisis completados o fallidos");
        }
        analisis.setEstado(EstadoAnalisis.DESCARTADO);
    }

    // ---------------------------------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------------------------------

    private AnalisisImagen propio(UUID id) {
        return analisisRepositorio.findByIdAndUsuarioId(id, seguridadUtils.usuarioActualId())
                .orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADO));
    }

    private static void validarIndicesCandidatos(List<Integer> indices, int cantidadCandidatos) {
        Set<Integer> usados = new HashSet<>();
        for (int i = 0; i < indices.size(); i++) {
            Integer indice = indices.get(i);
            if (indice != null && (indice >= cantidadCandidatos || !usados.add(indice))) {
                throw invalida("alimentos[" + i + "].indiceCandidato",
                        indice >= cantidadCandidatos
                                ? "El índice no corresponde a un candidato del análisis"
                                : "Un candidato solo puede confirmarse una vez");
            }
        }
    }

    private static ObjectNode aJson(Correcciones correcciones) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.put("aceptadosSinCambio", correcciones.aceptadosSinCambio());
        json.put("gramosModificados", correcciones.gramosModificados());
        json.put("alimentoCambiado", correcciones.alimentoCambiado());
        json.put("eliminados", correcciones.eliminados());
        json.put("anadidos", correcciones.anadidos());
        json.put("errorMedioGramosPct", correcciones.errorMedioGramosPct());
        return json;
    }

    private static void validar(MultipartFile imagen, TipoComida tipoComida, String descripcion) {
        if (imagen == null || imagen.isEmpty()) {
            throw invalida("imagen", "La foto es obligatoria");
        }
        if (tipoComida == null) {
            throw invalida("tipoComida", "El tipo de comida es obligatorio");
        }
        if (descripcion != null && descripcion.length() > LONGITUD_MAXIMA_DESCRIPCION) {
            throw invalida("descripcion", "La descripción no puede superar los " + LONGITUD_MAXIMA_DESCRIPCION + " caracteres");
        }
    }

    private static byte[] bytes(MultipartFile imagen) {
        try {
            return imagen.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se ha podido leer la foto subida", e);
        }
    }

    static AnalisisResponse respuesta(AnalisisImagen analisis) {
        List<CandidatoAnalisis> candidatos = analisis.getCandidatos();
        Nutrientes total = candidatos.stream()
                .filter(candidato -> !candidato.coincidencias().isEmpty())
                .map(candidato -> candidato.coincidencias().get(0).estimados())
                .reduce(Nutrientes.CERO, Nutrientes::sumar)
                .redondear();
        return new AnalisisResponse(analisis.getId(), analisis.getTipo(), analisis.getEstado(),
                analisis.getTipoComida(), candidatos, total, analisis.getErrorMensaje(), analisis.getCreadoEn(),
                analisis.getCompletadoEn());
    }

    private static PeticionInvalidaException invalida(String campo, String mensaje) {
        return new PeticionInvalidaException(DATOS_NO_VALIDOS, List.of(new ErrorCampo(campo, mensaje)));
    }
}
